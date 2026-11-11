# Split Sport API

Split Sport is Split for sports clubs: a group is a team with a pot (teamkas), a treasurer,
contributions and a fine list. One REST API serves the web app, the Android app and the iOS app. The source of truth is
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
Member       { id, name, kind: "player" | "pot",                         // "pot" = the team pot, one per team
               userId: string | null, iban: string | null }              // userId null = added by name, no account
GroupSummary { id, name, color, sport, club: string | null, season: string | null, currency,
               memberCount,                                              // team members, the pot not counted
               myMemberId, myBalanceCents, lastActivityAt }
GroupDetail  { id, name, color, sport, club, season, currency, inviteCode, myMemberId,
               potMemberId,                                              // the pot; also the last entry of members
               treasurerMemberId: string | null,                         // keeps the pot's money; pay to their IBAN
               members: Member[],                                        // team members in join order, then the pot
               balances:  { memberId, balanceCents, potCents, peerCents }[],
               transfers: { fromMemberId, toMemberId, amountCents }[],   // first with the pot, then among members
               pot: { cashCents, toReceiveCents, toPayCents },
               fines: { id, name, amountCents }[],                       // the fine list (boetelijst)
               fineStats: { memberId, count, totalCents }[],             // most fines first; members without are left out
               totalSpentCents }                                         // expenses only, no charges or payments
Expense      { id, groupId, kind: "expense" | "settlement" | "fine" | "contribution", description, amountCents,
               paidById,                       // member id; settlement: who paid; fine/contribution: always the pot
               date, splitMode: "equal" | "exact" | "shares" | "items",
               shares: { memberId, amountCents, weight: number | null }[],   // settlement: one share, the receiver
               items:  { name, amountCents, memberIds: string[] }[],        // only for splitMode "items"
               createdAt, updatedAt }
```

`color` is one of `amber`, `rose`, `green`, `coral`, `blue`, `lime`, `teal`, `slate`: the eight
highlighters of the house style (see `docs/design.md`). A member's highlighter is their index in
`members`; the pot is last and has none.

`sport` is one of `voetbal`, `hockey`, `korfbal`, `volleybal`, `handbal`, `basketbal`, `tennis`,
`padel`, `rugby`, `waterpolo`, `overig`.

### Balances and the pot

`balanceCents` is a member's total (> 0: gets money back). It is the sum of two sides:

- `potCents`: what they have with the pot. `< 0`: they still owe the pot (unpaid contributions and
  fines); `> 0`: the pot owes them (they advanced something for the team). For the pot's own entry
  it is the pot's total: `> 0` when members still owe it.
- `peerCents`: what they have with other members, from ordinary shared expenses.

`pot.cashCents` is the kasstand: payments into the pot, minus payments out of it and expenses the
pot paid. Fines and contributions are claims and only become cash when someone pays the pot.
`pot.toReceiveCents` and `pot.toPayCents` add up what members owe the pot and what it owes them.

How the pot appears in entries:

| What happened | Recorded as |
| --- | --- |
| Everyone is asked for € 25 | `POST /charges` with `type: "contribution"`: paid by the pot, a share of 2500 per member |
| Bas gets a fine of € 2 | `POST /charges` with `type: "fine"`: paid by the pot, one share of 200 for Bas |
| Bas pays the pot € 27 | `POST /settlements` from Bas to the pot |
| The pot pays the referee € 30 | `POST /expenses` with `paidById` the pot and a split over the pot alone |
| Chris bought balls for the team | `POST /expenses` paid by Chris with a split over the pot alone; the pot now owes Chris |
| The pot pays Chris back | `POST /settlements` from the pot to Chris |

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
| POST | `/groups` | `{ name, color?, sport?, club?, season?, currency? }` | 201 `GroupDetail`; creates the pot, makes the caller treasurer and adds a default fine list |
| GET | `/groups/:id` | – | `GroupDetail` |
| PATCH | `/groups/:id` | `{ name?, color?, sport?, club?, season?, treasurerMemberId? }` (`club`/`season`: `""` or `null` clears) | `GroupDetail` |
| POST | `/groups/:id/fines` | `{ name, amountCents }` | 201 `GroupDetail` (adds a line to the fine list) |
| PUT | `/groups/:id/fines/:fineId` | `{ name, amountCents }` | `GroupDetail` |
| DELETE | `/groups/:id/fines/:fineId` | – | `GroupDetail` |
| POST | `/groups/:id/leave` | – | 204 |
| POST | `/groups/:id/members` | `{ name }` | 201 `GroupDetail` (adds a member without account) |
| PATCH | `/groups/:id/members/:memberId` | `{ name }` | `GroupDetail` (only members without account) |
| DELETE | `/groups/:id/members/:memberId` | – | `GroupDetail`; 409 `member_in_use` when they are in expenses |
| GET | `/invites/:code` | – | `{ groupId, name, color, sport, club, memberCount, alreadyMember, unclaimedMembers: {id, name}[] }` |
| POST | `/invites/:code/join` | `{ memberId? }` | `GroupDetail`; with `memberId` the caller becomes that existing member |
| GET | `/groups/:id/expenses` | – | `{ expenses: Expense[] }`, newest date first, settlements included |
| POST | `/groups/:id/expenses` | `{ description, amountCents, date, paidById, split }` | 201 `Expense` |
| GET | `/groups/:id/expenses/:expenseId` | – | `Expense` |
| PUT | `/groups/:id/expenses/:expenseId` | same as POST | `Expense` |
| DELETE | `/groups/:id/expenses/:expenseId` | – | 204 (also deletes a settlement) |
| POST | `/groups/:id/charges` | `{ type: "fine" \| "contribution", description, amountCents, memberIds, date? }` (`amountCents` is per member) | 201 `Expense` with that `kind`, paid by the pot |
| POST | `/groups/:id/settlements` | `{ fromMemberId, toMemberId, amountCents, date? }` | 201 `Expense` with `kind: "settlement"` |
| POST | `/receipts/scan` | `{ imageBase64, mediaType }` | `{ merchant, date, totalCents, items: {name, amountCents}[] }` |

Payments, fines and contributions cannot be edited: `PUT` only accepts `kind: "expense"` and answers
404 for the others. Delete them (`DELETE /groups/:id/expenses/:expenseId`) and record them again.
The pot cannot be renamed or removed (403), claimed with an invite (409) or charged (400). The
treasurer can be any team member, also one without an account; the clients then have no IBAN to show.

Every endpoint except `/config`, `/auth/register` and `/auth/login` needs a token, the invite
endpoints included.

Receipt scanning accepts `image/jpeg`, `image/png`, `image/webp` and `image/gif`, at most about 8 MB of
image. Clients scale photos down to 1600 px on the long side and send JPEG. `merchant`, `date` and
`totalCents` can be `null`. The endpoint answers `503 scan_unavailable` when the server has no
Anthropic API key; check `GET /config` and hide the scan button in that case.

Invite codes are 8 characters (`A–Z`, `2–9`), case-insensitive. The share link is
`<server>/join/<code>`; the mobile apps also open `splitsport://join/<code>` (their own scheme, so Split and Split Sport can be installed side by side).

## Error codes

Clients show `message` and only need to act on `unauthorized` and `scan_unavailable`; the rest is
listed for completeness.

| Status | Code | When |
| --- | --- | --- |
| 400 | `invalid_json`, `validation_error` | The body is not valid JSON or a field is wrong |
| 400 | `invalid_amount`, `no_participants`, `duplicate_member`, `invalid_weight`, `exact_sum_mismatch`, `invalid_item`, `item_unassigned` | The split cannot be computed |
| 400 | `unknown_member` | A member id does not belong to the group |
| 401 | `unauthorized`, `invalid_credentials` | Missing or expired token; wrong e-mail or password |
| 403 | `forbidden` | Renaming or removing a member that has an account |
| 404 | `not_found` | Unknown group, expense, member, invite code or path |
| 409 | `email_taken`, `member_in_use`, `member_taken` | Registering twice; removing a member with expenses; claiming a member someone else took |
| 413 | `too_large` | Body over 256 KB (12 MB for a receipt photo) |
| 422 | `no_receipt`, `scan_failed` | The photo shows no receipt, or could not be read |
| 429 | `rate_limited` | Too many login attempts or scans; see `Retry-After` |
| 502, 503 | `scan_failed`, `scan_busy`, `scan_unavailable` | The receipt reader failed, is busy, or is not configured |
| 500 | `internal_error` | Anything else |
