/**
 * Fills a running server with a small, realistic demo: a hockey team with a
 * pot, contributions, fines, team expenses and a few payments, plus an indoor
 * football team.
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
  return new Date(Date.now() - daysAgo * 86_400_000).toISOString().slice(0, 10);
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
const fleur = await account("Fleur");
const noor = await account("Noor");
await call("PATCH", "/me", sanne.token, { iban: "NL91ABNA0417164300" });

// ---- Dames 2: hockey, Sanne keeps the pot ---------------------------------
const team = await call<GroupDetail>("POST", "/groups", sanne.token, {
  name: "Dames 2",
  color: "rose",
  sport: "hockey",
  club: "MHC Oostpoort",
  season: "2026/27",
});
await call("POST", `/invites/${team.inviteCode}/join`, fleur.token, {});
await call("POST", `/invites/${team.inviteCode}/join`, noor.token, {});
await call("POST", `/groups/${team.id}/members`, sanne.token, { name: "Lotte" });
const full = await call<GroupDetail>("POST", `/groups/${team.id}/members`, sanne.token, { name: "Emma" });
const [s, f, n, l, e] = full.members.map((m) => m.id) as [string, string, string, string, string];
const pot = full.potMemberId;
const players = [s, f, n, l, e];

const charge = (body: unknown) => call("POST", `/groups/${team.id}/charges`, sanne.token, body);
const expense = (token: string, body: unknown) => call("POST", `/groups/${team.id}/expenses`, token, body);
const pay = (from: string, to: string, amountCents: number, date: string) =>
  call("POST", `/groups/${team.id}/settlements`, sanne.token, { fromMemberId: from, toMemberId: to, amountCents, date });

await charge({ type: "contribution", description: "Inleg eerste seizoenshelft", amountCents: 2500, memberIds: players, date: day(42) });
await pay(s, pot, 2500, day(41));
await pay(f, pot, 2500, day(40));
await pay(n, pot, 2500, day(38));
await pay(l, pot, 2500, day(30));

await expense(sanne.token, {
  description: "Scheidsrechter oefenwedstrijd", amountCents: 3000, date: day(35), paidById: pot,
  split: { mode: "equal", memberIds: [pot] },
});
await charge({ type: "fine", description: "Te laat", amountCents: 200, memberIds: [n], date: day(33) });
await charge({ type: "fine", description: "Niet afgemeld", amountCents: 500, memberIds: [e], date: day(28) });
await expense(fleur.token, {
  description: "Wedstrijdballen", amountCents: 4750, date: day(26), paidById: f,
  split: { mode: "equal", memberIds: [pot] },
});
await expense(sanne.token, {
  description: "Rit naar HC Westerkwartier (84 km)", amountCents: 1932, date: day(21), paidById: s,
  split: { mode: "equal", memberIds: [s, f, n, l] },
});
await charge({ type: "fine", description: "Gele kaart", amountCents: 500, memberIds: [n], date: day(21) });
await charge({ type: "fine", description: "Spullen vergeten", amountCents: 200, memberIds: [f], date: day(14) });
await expense(noor.token, {
  description: "Derde helft clubhuis", amountCents: 5600, date: day(7), paidById: n,
  split: {
    mode: "items",
    items: [
      { name: "Kan bier", amountCents: 1650, memberIds: [s, n, e] },
      { name: "2× Witte wijn", amountCents: 950, memberIds: [f, l] },
      { name: "Bitterballen", amountCents: 1250, memberIds: players },
      { name: "Tosti's", amountCents: 1400, memberIds: [f, n] },
    ],
  },
});
await charge({ type: "fine", description: "Te laat", amountCents: 200, memberIds: [n], date: day(3) });
await expense(sanne.token, {
  description: "Bloemen voor de trainer", amountCents: 2250, date: day(2), paidById: pot,
  split: { mode: "equal", memberIds: [pot] },
});

// ---- Zaalvoetbal: a smaller team where someone else keeps the pot ---------
const indoor = await call<GroupDetail>("POST", "/groups", fleur.token, {
  name: "Zaalvoetbal vrijdag",
  color: "green",
  sport: "voetbal",
  club: "SV Oostpoort",
  season: "2026/27",
});
const joined = await call<GroupDetail>("POST", `/invites/${indoor.inviteCode}/join`, sanne.token, {});
const [zf, zs] = joined.members.map((m) => m.id) as [string, string];
await call("POST", `/groups/${indoor.id}/charges`, fleur.token, {
  type: "contribution", description: "Zaalhuur november", amountCents: 1800, memberIds: [zf, zs], date: day(9),
});
await call("POST", `/groups/${indoor.id}/settlements`, fleur.token, {
  fromMemberId: zf, toMemberId: joined.potMemberId, amountCents: 1800, date: day(8),
});

console.log(`Demo data created on ${base}. Log in as sanne@example.nl / ${PASSWORD}`);
