# Split API

One REST API serves the web app, the Android app and the iOS app. The source of truth is
`apps/api/src/app.ts` (routes), `apps/api/src/schemas.ts` (input validation) and
`packages/core/src/api-types.ts` (JSON shapes).

- Base path: `/api`. JSON in, JSON out, `camelCase` keys.
- Money is always **integer cents** (`amountCents: 1250` is € 12,50).
- Dates are calendar dates, `YYYY-MM-DD`. Timestamps (`createdAt`) are ISO 8601 in UTC.
- Authentication: `Authorization: Bearer <token>`. The token comes from register or login and stays
  valid until logout.
- Errors: a non-2xx status with `{"error": {"code": "…", "message": "…"}}`. `message` is Dutch and
  written to be shown to the user as-is; `code` is stable for programmatic handling.
  `401 unauthorized` means the token is no longer valid: drop it and show the login screen.

## Shapes

```ts
User         { id, name, email, iban: string | null }
Member       { id, name, userId: string | null, iban: string | null }   // userId null = added by name, no account
GroupSummary { id, name, color, currency, memberCount, myMemberId, myBalanceCents, lastActivityAt }
GroupDetail  { id, name, color, currency, inviteCode, myMemberId,
               members: Member[],
               balances:  { memberId, balanceCents }[],                  // > 0: gets money back, < 0: owes
               transfers: { fromMemberId, toMemberId, amountCents }[],   // suggested payments that settle everything
               totalSpentCents }
Expense      { id, groupId, kind: "expense" | "settlement", description, amountCents,
               paidById,                       // member id; for a settlement: who paid back
               date, splitMode: "equal" | "exact" | "shares" | "items",
               shares: { memberId, amountCents, weight: number | null }[],   // settlement: one share, the receiver
               items:  { name, amountCents, memberIds: string[] }[],        // only for splitMode "items"
               createdAt, updatedAt }
```

`color` is one of `blue`, `amber`, `teal`, `rose`, `green`, `coral`, `lime`, `slate` (see `docs/design.md`).

A split is sent as one of:

```jsonc
{ "mode": "equal",  "memberIds": ["m1", "m2"] }
{ "mode": "exact",  "amounts": [{ "memberId": "m1", "amountCents": 1250 }] }        // must add up to amountCents
{ "mode": "shares", "weights": [{ "memberId": "m1", "weight": 2 }] }                 // whole numbers, 0 = not in
{ "mode": "items",  "items": [{ "name": "Pizza", "amountCents": 1100, "memberIds": ["m1"] }] }
```

The server computes the shares (`computeShares` in `packages/core/src/split.ts`) and is authoritative.
Clients implement the same function for live previews; `spec/split-vectors.json` holds the test
vectors every implementation must pass. For `items`, a difference between `amountCents` and the sum
of the lines (tip, discount) is spread in proportion to what everyone had.

To edit an expense, rebuild the split from the stored expense:
`equal` → `shares[].memberId`; `exact` → `shares[]`; `shares` → `shares[].weight`; `items` → `items`.

## Endpoints

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| GET | `/config` | – | `{ receiptScanEnabled }` (no auth) |
| POST | `/auth/register` | `{ name, email, password }` (password ≥ 8) | 201 `{ token, user }` |
| POST | `/auth/login` | `{ email, password }` | `{ token, user }` |
| POST | `/auth/logout` | – | 204 |
| GET | `/me` | – | `User` |
| PATCH | `/me` | `{ name?, iban? }` (`iban: ""` or `null` clears it) | `User` |
| DELETE | `/me` | – | 204, deletes the account |
| GET | `/groups` | – | `{ groups: GroupSummary[] }`, most recent activity first |
| POST | `/groups` | `{ name, color?, currency? }` | 201 `GroupDetail` |
| GET | `/groups/:id` | – | `GroupDetail` |
| PATCH | `/groups/:id` | `{ name?, color? }` | `GroupDetail` |
| POST | `/groups/:id/leave` | – | 204 |
| POST | `/groups/:id/members` | `{ name }` | 201 `GroupDetail` (adds a member without account) |
| PATCH | `/groups/:id/members/:memberId` | `{ name }` | `GroupDetail` (only members without account) |
| DELETE | `/groups/:id/members/:memberId` | – | `GroupDetail`; 409 `member_in_use` when they are in expenses |
| GET | `/invites/:code` | – | `{ groupId, name, color, memberCount, alreadyMember, unclaimedMembers: {id, name}[] }` |
| POST | `/invites/:code/join` | `{ memberId? }` | `GroupDetail`; with `memberId` the caller becomes that existing member |
| GET | `/groups/:id/expenses` | – | `{ expenses: Expense[] }`, newest date first, settlements included |
| POST | `/groups/:id/expenses` | `{ description, amountCents, date, paidById, split }` | 201 `Expense` |
| GET | `/groups/:id/expenses/:expenseId` | – | `Expense` |
| PUT | `/groups/:id/expenses/:expenseId` | same as POST | `Expense` |
| DELETE | `/groups/:id/expenses/:expenseId` | – | 204 (also deletes a settlement) |
| POST | `/groups/:id/settlements` | `{ fromMemberId, toMemberId, amountCents, date? }` | 201 `Expense` with `kind: "settlement"` |
| POST | `/receipts/scan` | `{ imageBase64, mediaType }` | `{ merchant, date, totalCents, items: {name, amountCents}[] }` |

Receipt scanning accepts `image/jpeg`, `image/png`, `image/webp` and `image/gif`, at most about 8 MB of
image. Clients scale photos down to 1600 px on the long side and send JPEG. `merchant`, `date` and
`totalCents` can be `null`. The endpoint answers `503 scan_unavailable` when the server has no
Anthropic API key; check `GET /config` and hide the scan button in that case.

Invite codes are 8 characters (`A–Z`, `2–9`), case-insensitive. The share link is
`<server>/join/<code>`; the mobile apps also open `split://join/<code>`.
