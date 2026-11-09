/**
 * Fills a running server with a small, realistic demo: four housemates, two
 * groups, a month of expenses and one payment.
 *
 *   pnpm --filter @split/api seed            # against http://localhost:8787
 *   SPLIT_API_URL=https://… pnpm --filter @split/api seed
 *
 * Log in afterwards as sanne@example.nl with the password "split-demo".
 */
import type { AuthResponse, GroupDetail } from "@split/core";

const base = `${process.env.SPLIT_API_URL ?? "http://localhost:8787"}/api`;
const PASSWORD = "split-demo";

async function call<T>(method: string, path: string, token?: string, body?: unknown): Promise<T> {
  const response = await fetch(base + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`${method} ${path} -> ${response.status} ${text}`);
  return (text ? JSON.parse(text) : undefined) as T;
}

/** A date `daysAgo` days back, as YYYY-MM-DD. */
function day(daysAgo: number): string {
  const date = new Date(Date.now() - daysAgo * 86_400_000);
  return date.toISOString().slice(0, 10);
}

async function account(name: string): Promise<AuthResponse> {
  const email = `${name.toLowerCase()}@example.nl`;
  try {
    return await call<AuthResponse>("POST", "/auth/register", undefined, { name, email, password: PASSWORD });
  } catch {
    return call<AuthResponse>("POST", "/auth/login", undefined, { email, password: PASSWORD });
  }
}

const sanne = await account("Sanne");
const daan = await account("Daan");
const yusuf = await account("Yusuf");
await call("PATCH", "/me", daan.token, { iban: "NL91ABNA0417164300" });

// ---- Huis Vondelstraat: three accounts and one housemate added by name ----
const house = await call<GroupDetail>("POST", "/groups", sanne.token, { name: "Huis Vondelstraat", color: "blue" });
await call("POST", `/invites/${house.inviteCode}/join`, daan.token, {});
await call("POST", `/invites/${house.inviteCode}/join`, yusuf.token, {});
const withLotte = await call<GroupDetail>("POST", `/groups/${house.id}/members`, sanne.token, { name: "Lotte" });
const [s, d, y, l] = withLotte.members.map((m) => m.id) as [string, string, string, string];
const everyone = [s, d, y, l];

const expense = (groupId: string, token: string, body: unknown) => call("POST", `/groups/${groupId}/expenses`, token, body);

await expense(house.id, sanne.token, {
  description: "Boodschappen Albert Heijn", amountCents: 8743, date: day(24), paidById: s,
  split: { mode: "equal", memberIds: everyone },
});
await expense(house.id, daan.token, {
  description: "Internet en tv", amountCents: 5250, date: day(21), paidById: d,
  split: { mode: "equal", memberIds: everyone },
});
await expense(house.id, yusuf.token, {
  description: "Energie oktober", amountCents: 18600, date: day(17), paidById: y,
  split: { mode: "shares", weights: [{ memberId: s, weight: 2 }, { memberId: d, weight: 2 }, { memberId: y, weight: 2 }, { memberId: l, weight: 1 }] },
});
await expense(house.id, sanne.token, {
  description: "Schoonmaakspullen", amountCents: 2315, date: day(12), paidById: s,
  split: { mode: "equal", memberIds: everyone },
});
await expense(house.id, daan.token, {
  description: "Café De Zon", amountCents: 8400, date: day(6), paidById: d,
  split: {
    mode: "items",
    items: [
      { name: "2× Bier", amountCents: 900, memberIds: [d, y] },
      { name: "Witte wijn", amountCents: 650, memberIds: [s] },
      { name: "Bitterballen", amountCents: 1250, memberIds: [s, d, y] },
      { name: "Burger met friet", amountCents: 1850, memberIds: [d] },
      { name: "Saté", amountCents: 1750, memberIds: [y] },
      { name: "Geitenkaassalade", amountCents: 1600, memberIds: [s] },
    ],
  },
});
await expense(house.id, sanne.token, {
  description: "Cadeau Lotte", amountCents: 4500, date: day(3), paidById: s,
  split: { mode: "exact", amounts: [{ memberId: s, amountCents: 1500 }, { memberId: d, amountCents: 1500 }, { memberId: y, amountCents: 1500 }] },
});
await call("POST", `/groups/${house.id}/settlements`, yusuf.token, { fromMemberId: s, toMemberId: y, amountCents: 2000, date: day(2) });
await expense(house.id, yusuf.token, {
  description: "Boodschappen Jumbo", amountCents: 6412, date: day(1), paidById: y,
  split: { mode: "equal", memberIds: everyone },
});

// ---- Weekend Texel: a trip with two of them ----
const trip = await call<GroupDetail>("POST", "/groups", sanne.token, { name: "Weekend Texel", color: "teal" });
const tripJoined = await call<GroupDetail>("POST", `/invites/${trip.inviteCode}/join`, daan.token, {});
const [ts, td] = tripJoined.members.map((m) => m.id) as [string, string];
await expense(trip.id, sanne.token, {
  description: "Huisje", amountCents: 32000, date: day(40), paidById: ts, split: { mode: "equal", memberIds: [ts, td] },
});
await expense(trip.id, daan.token, {
  description: "Boot en benzine", amountCents: 9480, date: day(39), paidById: td, split: { mode: "equal", memberIds: [ts, td] },
});
await expense(trip.id, daan.token, {
  description: "Fietsen huren", amountCents: 5600, date: day(38), paidById: td, split: { mode: "equal", memberIds: [ts, td] },
});

console.log(`Demo data created on ${base}. Log in as sanne@example.nl / ${PASSWORD}`);
