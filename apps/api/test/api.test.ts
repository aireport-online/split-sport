import type { AuthResponse, Expense, GroupDetail, GroupSummary, InvitePreview, ReceiptScan } from "@split/core";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app";
import { openDatabase } from "../src/db";
import { HttpError } from "../src/errors";
import { normalizeReceipt, type ReceiptScanner } from "../src/receipts";

type App = ReturnType<typeof createApp>;

const fakeScanner: ReceiptScanner = {
  async scan(imageBase64) {
    if (imageBase64 === "bm8=") throw new HttpError(422, "no_receipt", "We zien geen bon op deze foto.");
    return {
      merchant: "Café De Zon",
      date: "2026-11-01",
      totalCents: 2350,
      items: [
        { name: "2× Bier", amountCents: 900 },
        { name: "Bitterballen", amountCents: 1250 },
      ],
    };
  },
};

let app: App;

beforeEach(() => {
  app = createApp({ db: openDatabase(":memory:"), receiptScanner: fakeScanner, rateLimits: false });
});

async function call<T = unknown>(
  method: string,
  path: string,
  options: { token?: string; body?: unknown } = {},
): Promise<{ status: number; data: T }> {
  const response = await app.request(`/api${path}`, {
    method,
    headers: {
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
      ...(options.body !== undefined ? { "Content-Type": "application/json" } : {}),
    },
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });
  const text = await response.text();
  return { status: response.status, data: (text ? JSON.parse(text) : null) as T };
}

async function register(name: string): Promise<AuthResponse> {
  const { status, data } = await call<AuthResponse>("POST", "/auth/register", {
    body: { name, email: `${name.toLowerCase()}@example.nl`, password: "geheim-wachtwoord" },
  });
  expect(status).toBe(201);
  return data;
}

async function createGroup(token: string, name = "Weekendje Texel"): Promise<GroupDetail> {
  const { status, data } = await call<GroupDetail>("POST", "/groups", { token, body: { name, color: "teal" } });
  expect(status).toBe(201);
  return data;
}

const balanceOf = (group: GroupDetail, memberId: string) =>
  group.balances.find((b) => b.memberId === memberId)!.balanceCents;

describe("accounts", () => {
  it("registers, logs in, reads and updates the profile", async () => {
    const anna = await register("Anna");
    expect(anna.user).toMatchObject({ name: "Anna", email: "anna@example.nl", iban: null });

    const login = await call<AuthResponse>("POST", "/auth/login", {
      body: { email: "ANNA@example.nl ", password: "geheim-wachtwoord" },
    });
    expect(login.status).toBe(200);

    const me = await call("GET", "/me", { token: login.data.token });
    expect(me.data).toMatchObject({ id: anna.user.id });

    const patched = await call("PATCH", "/me", {
      token: anna.token,
      body: { name: "Anna de Vries", iban: "nl91 abna 0417 1643 00" },
    });
    expect(patched.data).toMatchObject({ name: "Anna de Vries", iban: "NL91ABNA0417164300" });

    const cleared = await call("PATCH", "/me", { token: anna.token, body: { iban: "" } });
    expect(cleared.data).toMatchObject({ iban: null });
  });

  it("rejects bad input with a readable message", async () => {
    const short = await call<{ error: { code: string; message: string } }>("POST", "/auth/register", {
      body: { name: "Bas", email: "bas@example.nl", password: "kort" },
    });
    expect(short.status).toBe(400);
    expect(short.data.error.message).toMatch(/8 tekens/);

    await register("Bas");
    const duplicate = await call<{ error: { code: string } }>("POST", "/auth/register", {
      body: { name: "Bas", email: "BAS@example.nl", password: "geheim-wachtwoord" },
    });
    expect(duplicate.status).toBe(409);
    expect(duplicate.data.error.code).toBe("email_taken");

    const wrong = await call("POST", "/auth/login", { body: { email: "bas@example.nl", password: "verkeerd" } });
    expect(wrong.status).toBe(401);
    const iban = await call("PATCH", "/me", { token: (await register("Chris")).token, body: { iban: "geen iban" } });
    expect(iban.status).toBe(400);
  });

  it("requires a valid session and ends it on logout", async () => {
    expect((await call("GET", "/groups")).status).toBe(401);
    expect((await call("GET", "/groups", { token: "onzin" })).status).toBe(401);
    const anna = await register("Anna");
    expect((await call("POST", "/auth/logout", { token: anna.token })).status).toBe(204);
    expect((await call("GET", "/me", { token: anna.token })).status).toBe(401);
  });
});

describe("groups and members", () => {
  it("creates a group with the creator as first member", async () => {
    const anna = await register("Anna");
    const group = await createGroup(anna.token);
    expect(group).toMatchObject({ name: "Weekendje Texel", color: "teal", currency: "EUR", totalSpentCents: 0 });
    expect(group.inviteCode).toMatch(/^[A-Z2-9]{8}$/);
    expect(group.members).toEqual([{ id: group.myMemberId, name: "Anna", userId: anna.user.id, iban: null }]);

    const list = await call<{ groups: GroupSummary[] }>("GET", "/groups", { token: anna.token });
    expect(list.data.groups).toMatchObject([{ id: group.id, memberCount: 1, myBalanceCents: 0 }]);
  });

  it("hides groups from people who are not in them", async () => {
    const anna = await register("Anna");
    const bas = await register("Bas");
    const group = await createGroup(anna.token);
    expect((await call("GET", `/groups/${group.id}`, { token: bas.token })).status).toBe(404);
    expect((await call("GET", `/groups/${group.id}/expenses`, { token: bas.token })).status).toBe(404);
    expect((await call("GET", "/groups", { token: bas.token })).data).toEqual({ groups: [] });
  });

  it("adds, renames and removes members without an account", async () => {
    const anna = await register("Anna");
    const group = await createGroup(anna.token);
    const added = await call<GroupDetail>("POST", `/groups/${group.id}/members`, { token: anna.token, body: { name: "Opa Jan" } });
    const opa = added.data.members[1]!;
    expect(opa).toMatchObject({ name: "Opa Jan", userId: null });

    const renamed = await call<GroupDetail>("PATCH", `/groups/${group.id}/members/${opa.id}`, {
      token: anna.token,
      body: { name: "Opa" },
    });
    expect(renamed.data.members[1]!.name).toBe("Opa");

    const own = await call("PATCH", `/groups/${group.id}/members/${group.myMemberId}`, { token: anna.token, body: { name: "X" } });
    expect(own.status).toBe(403);

    const removed = await call<GroupDetail>("DELETE", `/groups/${group.id}/members/${opa.id}`, { token: anna.token });
    expect(removed.data.members).toHaveLength(1);
  });

  it("lets someone join by invite code, as a new member or as an existing name", async () => {
    const anna = await register("Anna");
    const bas = await register("Bas");
    const chris = await register("Chris");
    const group = await createGroup(anna.token);
    const withPlaceholder = await call<GroupDetail>("POST", `/groups/${group.id}/members`, {
      token: anna.token,
      body: { name: "Bas (nog geen account)" },
    });
    const placeholder = withPlaceholder.data.members[1]!;

    const preview = await call<InvitePreview>("GET", `/invites/${group.inviteCode.toLowerCase()}`, { token: bas.token });
    expect(preview.data).toMatchObject({
      name: "Weekendje Texel",
      memberCount: 2,
      alreadyMember: false,
      unclaimedMembers: [{ id: placeholder.id, name: "Bas (nog geen account)" }],
    });

    const claimed = await call<GroupDetail>("POST", `/invites/${group.inviteCode}/join`, {
      token: bas.token,
      body: { memberId: placeholder.id },
    });
    expect(claimed.data.myMemberId).toBe(placeholder.id);
    expect(claimed.data.members[1]).toMatchObject({ name: "Bas", userId: bas.user.id });

    const taken = await call("POST", `/invites/${group.inviteCode}/join`, { token: chris.token, body: { memberId: placeholder.id } });
    expect(taken.status).toBe(409);

    const joined = await call<GroupDetail>("POST", `/invites/${group.inviteCode}/join`, { token: chris.token, body: {} });
    expect(joined.data.members.map((m) => m.name)).toEqual(["Anna", "Bas", "Chris"]);

    const again = await call<GroupDetail>("POST", `/invites/${group.inviteCode}/join`, { token: chris.token, body: {} });
    expect(again.data.members).toHaveLength(3);

    expect((await call("GET", "/invites/BESTAATNIET", { token: bas.token })).status).toBe(404);
  });

  it("renames a user in every group", async () => {
    const anna = await register("Anna");
    const group = await createGroup(anna.token);
    await call("PATCH", "/me", { token: anna.token, body: { name: "Annabel", iban: "NL91ABNA0417164300" } });
    const detail = await call<GroupDetail>("GET", `/groups/${group.id}`, { token: anna.token });
    expect(detail.data.members[0]).toMatchObject({ name: "Annabel", iban: "NL91ABNA0417164300" });
  });
});

describe("expenses, balances and settling up", () => {
  async function trio() {
    const anna = await register("Anna");
    const bas = await register("Bas");
    const chris = await register("Chris");
    const group = await createGroup(anna.token);
    await call("POST", `/invites/${group.inviteCode}/join`, { token: bas.token, body: {} });
    const detail = (await call<GroupDetail>("POST", `/invites/${group.inviteCode}/join`, { token: chris.token, body: {} })).data;
    const [a, b, c] = detail.members.map((m) => m.id) as [string, string, string];
    return { anna, bas, chris, groupId: group.id, a, b, c };
  }

  it("splits an expense equally and updates everyone's balance", async () => {
    const { anna, bas, groupId, a, b, c } = await trio();
    const created = await call<Expense>("POST", `/groups/${groupId}/expenses`, {
      token: anna.token,
      body: { description: "Boodschappen", amountCents: 1000, date: "2026-11-02", paidById: a, split: { mode: "equal", memberIds: [a, b, c] } },
    });
    expect(created.status).toBe(201);
    expect(created.data).toMatchObject({
      kind: "expense",
      splitMode: "equal",
      shares: [
        { memberId: a, amountCents: 334, weight: null },
        { memberId: b, amountCents: 333, weight: null },
        { memberId: c, amountCents: 333, weight: null },
      ],
    });

    const group = (await call<GroupDetail>("GET", `/groups/${groupId}`, { token: bas.token })).data;
    expect(group.totalSpentCents).toBe(1000);
    expect([balanceOf(group, a), balanceOf(group, b), balanceOf(group, c)]).toEqual([666, -333, -333]);
    expect(group.transfers).toEqual([
      { fromMemberId: b, toMemberId: a, amountCents: 333 },
      { fromMemberId: c, toMemberId: a, amountCents: 333 },
    ]);

    const list = await call<{ groups: GroupSummary[] }>("GET", "/groups", { token: bas.token });
    expect(list.data.groups[0]!.myBalanceCents).toBe(-333);
  });

  it("supports exact amounts, shares and receipt items", async () => {
    const { anna, groupId, a, b, c } = await trio();
    const post = (body: unknown) => call<Expense>("POST", `/groups/${groupId}/expenses`, { token: anna.token, body });

    const exact = await post({
      description: "Taxi", amountCents: 3000, date: "2026-11-02", paidById: b,
      split: { mode: "exact", amounts: [{ memberId: a, amountCents: 1000 }, { memberId: c, amountCents: 2000 }] },
    });
    expect(exact.data.shares.map((s) => s.amountCents)).toEqual([1000, 2000]);

    const shares = await post({
      description: "Huisje", amountCents: 9000, date: "2026-11-03", paidById: a,
      split: { mode: "shares", weights: [{ memberId: a, weight: 2 }, { memberId: b, weight: 1 }, { memberId: c, weight: 0 }] },
    });
    expect(shares.data.shares).toEqual([
      { memberId: a, amountCents: 6000, weight: 2 },
      { memberId: b, amountCents: 3000, weight: 1 },
    ]);

    const items = await post({
      description: "Café De Zon", amountCents: 4400, date: "2026-11-04", paidById: c,
      split: {
        mode: "items",
        items: [
          { name: "Steak", amountCents: 3000, memberIds: [a] },
          { name: "Salade", amountCents: 1000, memberIds: [b] },
        ],
      },
    });
    expect(items.data.items).toEqual([
      { name: "Steak", amountCents: 3000, memberIds: [a] },
      { name: "Salade", amountCents: 1000, memberIds: [b] },
    ]);
    expect(items.data.shares.map((s) => s.amountCents)).toEqual([3300, 1100]);

    const list = await call<{ expenses: Expense[] }>("GET", `/groups/${groupId}/expenses`, { token: anna.token });
    expect(list.data.expenses.map((e) => e.description)).toEqual(["Café De Zon", "Huisje", "Taxi"]);

    const group = (await call<GroupDetail>("GET", `/groups/${groupId}`, { token: anna.token })).data;
    expect(group.balances.reduce((sum, x) => sum + x.balanceCents, 0)).toBe(0);
    // anna: paid 9000, owes 1000 + 6000 + 3300
    expect(balanceOf(group, a)).toBe(-1300);
  });

  it("rejects splits that do not add up or use members from elsewhere", async () => {
    const { anna, groupId, a, b } = await trio();
    const other = await createGroup(anna.token, "Andere groep");
    const post = (body: unknown) =>
      call<{ error: { code: string; message: string } }>("POST", `/groups/${groupId}/expenses`, { token: anna.token, body });
    const base = { description: "Test", amountCents: 1000, date: "2026-11-02", paidById: a };

    const mismatch = await post({ ...base, split: { mode: "exact", amounts: [{ memberId: a, amountCents: 400 }, { memberId: b, amountCents: 500 }] } });
    expect(mismatch.status).toBe(400);
    expect(mismatch.data.error).toEqual({ code: "exact_sum_mismatch", message: "De bedragen tellen niet op tot het totaal." });

    expect((await post({ ...base, split: { mode: "equal", memberIds: [] } })).data.error.code).toBe("no_participants");
    expect((await post({ ...base, amountCents: 0, split: { mode: "equal", memberIds: [a] } })).data.error.code).toBe("invalid_amount");
    expect((await post({ ...base, split: { mode: "equal", memberIds: [other.myMemberId] } })).data.error.code).toBe("unknown_member");
    expect((await post({ ...base, date: "2026-02-30", split: { mode: "equal", memberIds: [a] } })).data.error.code).toBe("validation_error");
    expect((await post({ ...base, description: "  ", split: { mode: "equal", memberIds: [a] } })).status).toBe(400);
  });

  it("edits and deletes an expense", async () => {
    const { anna, bas, groupId, a, b } = await trio();
    const created = await call<Expense>("POST", `/groups/${groupId}/expenses`, {
      token: anna.token,
      body: { description: "Pizza", amountCents: 2000, date: "2026-11-02", paidById: a, split: { mode: "equal", memberIds: [a, b] } },
    });
    const edited = await call<Expense>("PUT", `/groups/${groupId}/expenses/${created.data.id}`, {
      token: bas.token,
      body: {
        description: "Pizza en drinken", amountCents: 3000, date: "2026-11-02", paidById: b,
        split: { mode: "items", items: [{ name: "Pizza", amountCents: 2000, memberIds: [a, b] }, { name: "Wijn", amountCents: 1000, memberIds: [b] }] },
      },
    });
    expect(edited.data).toMatchObject({ id: created.data.id, description: "Pizza en drinken", paidById: b, splitMode: "items" });
    expect(edited.data.shares.map((s) => s.amountCents)).toEqual([1000, 2000]);

    const group = (await call<GroupDetail>("GET", `/groups/${groupId}`, { token: anna.token })).data;
    expect([balanceOf(group, a), balanceOf(group, b)]).toEqual([-1000, 1000]);

    expect((await call("DELETE", `/groups/${groupId}/expenses/${created.data.id}`, { token: anna.token })).status).toBe(204);
    expect((await call("GET", `/groups/${groupId}/expenses/${created.data.id}`, { token: anna.token })).status).toBe(404);
    const after = (await call<GroupDetail>("GET", `/groups/${groupId}`, { token: anna.token })).data;
    expect(after.balances.every((x) => x.balanceCents === 0)).toBe(true);
  });

  it("records a settlement that brings balances back to zero", async () => {
    const { anna, bas, groupId, a, b } = await trio();
    await call("POST", `/groups/${groupId}/expenses`, {
      token: anna.token,
      body: { description: "Diner", amountCents: 5000, date: "2026-11-02", paidById: a, split: { mode: "equal", memberIds: [a, b] } },
    });
    const settlement = await call<Expense>("POST", `/groups/${groupId}/settlements`, {
      token: bas.token,
      body: { fromMemberId: b, toMemberId: a, amountCents: 2500, date: "2026-11-05" },
    });
    expect(settlement.status).toBe(201);
    expect(settlement.data).toMatchObject({ kind: "settlement", paidById: b, shares: [{ memberId: a, amountCents: 2500 }] });

    const group = (await call<GroupDetail>("GET", `/groups/${groupId}`, { token: anna.token })).data;
    expect(group.balances.every((x) => x.balanceCents === 0)).toBe(true);
    expect(group.transfers).toEqual([]);
    expect(group.totalSpentCents).toBe(5000);

    const self = await call("POST", `/groups/${groupId}/settlements`, { token: bas.token, body: { fromMemberId: b, toMemberId: b, amountCents: 100 } });
    expect(self.status).toBe(400);
  });

  it("keeps a member's history when they leave, and frees members that are unused", async () => {
    const { anna, bas, chris, groupId, a, b, c } = await trio();
    await call("POST", `/groups/${groupId}/expenses`, {
      token: anna.token,
      body: { description: "Diner", amountCents: 5000, date: "2026-11-02", paidById: a, split: { mode: "equal", memberIds: [a, b] } },
    });
    const placeholder = (await call<GroupDetail>("POST", `/groups/${groupId}/members`, { token: anna.token, body: { name: "Dewi" } })).data.members[3]!;
    await call("POST", `/groups/${groupId}/expenses`, {
      token: anna.token,
      body: { description: "IJsje", amountCents: 300, date: "2026-11-02", paidById: a, split: { mode: "equal", memberIds: [placeholder.id] } },
    });
    expect((await call("DELETE", `/groups/${groupId}/members/${placeholder.id}`, { token: anna.token })).status).toBe(409);

    expect((await call("POST", `/groups/${groupId}/leave`, { token: bas.token })).status).toBe(204);
    expect((await call("POST", `/groups/${groupId}/leave`, { token: chris.token })).status).toBe(204);
    const group = (await call<GroupDetail>("GET", `/groups/${groupId}`, { token: anna.token })).data;
    expect(group.members.map((m) => [m.name, m.userId])).toEqual([
      ["Anna", anna.user.id],
      ["Bas", null],
      ["Dewi", null],
    ]);
    expect(balanceOf(group, b)).toBe(-2500);
    expect(group.members.some((m) => m.id === c)).toBe(false);
    expect((await call("GET", `/groups/${groupId}`, { token: bas.token })).status).toBe(404);
  });

  it("deletes an account, and the group with it when nobody is left", async () => {
    const { anna, bas, chris, groupId, a, b } = await trio();
    await call("POST", `/groups/${groupId}/expenses`, {
      token: anna.token,
      body: { description: "Diner", amountCents: 5000, date: "2026-11-02", paidById: a, split: { mode: "equal", memberIds: [a, b] } },
    });
    expect((await call("DELETE", "/me", { token: anna.token })).status).toBe(204);
    expect((await call("GET", "/me", { token: anna.token })).status).toBe(401);
    const group = (await call<GroupDetail>("GET", `/groups/${groupId}`, { token: bas.token })).data;
    expect(group.members[0]).toMatchObject({ name: "Anna", userId: null });
    expect(balanceOf(group, a)).toBe(2500);

    await call("DELETE", "/me", { token: bas.token });
    await call("DELETE", "/me", { token: chris.token });
    const dana = await register("Dana");
    expect((await call("GET", `/invites/${group.inviteCode}`, { token: dana.token })).status).toBe(404);
  });
});

describe("receipt scanning", () => {
  it("reports whether scanning is available", async () => {
    expect((await call("GET", "/config")).data).toEqual({ receiptScanEnabled: true });
    const without = createApp({ db: openDatabase(":memory:"), receiptScanner: null, rateLimits: false });
    expect(await (await without.request("/api/config")).json()).toEqual({ receiptScanEnabled: false });
  });

  it("returns the lines of a receipt", async () => {
    const anna = await register("Anna");
    const scan = await call<ReceiptScan>("POST", "/receipts/scan", { token: anna.token, body: { imageBase64: "amE=", mediaType: "image/jpeg" } });
    expect(scan.status).toBe(200);
    expect(scan.data.items).toHaveLength(2);
    expect(scan.data.totalCents).toBe(2350);

    const none = await call("POST", "/receipts/scan", { token: anna.token, body: { imageBase64: "bm8=", mediaType: "image/jpeg" } });
    expect(none.status).toBe(422);
    const type = await call("POST", "/receipts/scan", { token: anna.token, body: { imageBase64: "amE=", mediaType: "application/pdf" } });
    expect(type.status).toBe(400);
    expect((await call("POST", "/receipts/scan", { body: { imageBase64: "amE=", mediaType: "image/jpeg" } })).status).toBe(401);
  });

  it("cleans up what the model returns", () => {
    expect(
      normalizeReceipt({
        isReceipt: true,
        merchant: "  Albert Heijn ",
        date: "02-11-2026",
        totalCents: 0,
        items: [
          { name: " Melk ", amountCents: 129 },
          { name: "Bonuskorting", amountCents: -50 },
          { name: "", amountCents: 100 },
        ],
      }),
    ).toEqual({ merchant: "Albert Heijn", date: null, totalCents: null, items: [{ name: "Melk", amountCents: 129 }] });
    expect(() => normalizeReceipt({ isReceipt: false, merchant: null, date: null, totalCents: null, items: [] })).toThrow(HttpError);
  });
});

describe("rate limiting", () => {
  it("slows down repeated login attempts", async () => {
    const limited = createApp({ db: openDatabase(":memory:"), receiptScanner: null });
    let last = 0;
    for (let i = 0; i < 21; i++) {
      const response = await limited.request("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: "x@example.nl", password: "verkeerd" }),
      });
      last = response.status;
    }
    expect(last).toBe(429);
  });
});
