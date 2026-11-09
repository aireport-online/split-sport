import { describe, expect, it } from "vitest";
import { computeBalances, computeShares, suggestTransfers, type LedgerEntry } from "../src";

const members = ["anna", "bas", "chris", "dewi"];

describe("computeBalances", () => {
  it("is zero for everyone without entries", () => {
    expect(computeBalances(members, [])).toEqual(members.map((memberId) => ({ memberId, balanceCents: 0 })));
  });

  it("credits the payer and debits the participants", () => {
    const entries: LedgerEntry[] = [
      { amountCents: 9000, paidById: "anna", shares: computeShares(9000, { mode: "equal", memberIds: ["anna", "bas", "chris"] }) },
      { amountCents: 2000, paidById: "bas", shares: computeShares(2000, { mode: "equal", memberIds: ["bas", "dewi"] }) },
    ];
    expect(computeBalances(members, entries)).toEqual([
      { memberId: "anna", balanceCents: 6000 },
      { memberId: "bas", balanceCents: -2000 },
      { memberId: "chris", balanceCents: -3000 },
      { memberId: "dewi", balanceCents: -1000 },
    ]);
  });

  it("a settlement moves the debtor towards zero", () => {
    const entries: LedgerEntry[] = [
      { amountCents: 1000, paidById: "anna", shares: [{ memberId: "bas", amountCents: 1000 }] },
      { amountCents: 400, paidById: "bas", shares: [{ memberId: "anna", amountCents: 400 }] },
    ];
    const balances = computeBalances(["anna", "bas"], entries);
    expect(balances).toEqual([
      { memberId: "anna", balanceCents: 600 },
      { memberId: "bas", balanceCents: -600 },
    ]);
  });
});

describe("suggestTransfers", () => {
  it("returns nothing when everyone is square", () => {
    expect(suggestTransfers(computeBalances(members, []))).toEqual([]);
  });

  it("matches the largest debtor with the largest creditor", () => {
    const transfers = suggestTransfers([
      { memberId: "anna", balanceCents: 6000 },
      { memberId: "bas", balanceCents: -2000 },
      { memberId: "chris", balanceCents: -3000 },
      { memberId: "dewi", balanceCents: -1000 },
    ]);
    expect(transfers).toEqual([
      { fromMemberId: "chris", toMemberId: "anna", amountCents: 3000 },
      { fromMemberId: "bas", toMemberId: "anna", amountCents: 2000 },
      { fromMemberId: "dewi", toMemberId: "anna", amountCents: 1000 },
    ]);
  });

  it("settles every balance with at most n-1 payments", () => {
    let seed = 42;
    const random = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
    for (let round = 0; round < 200; round++) {
      const n = 2 + Math.floor(random() * 7);
      const balances = Array.from({ length: n }, (_, i) => ({
        memberId: `m${i}`,
        balanceCents: Math.floor(random() * 20000) - 10000,
      }));
      balances[0]!.balanceCents -= balances.reduce((sum, b) => sum + b.balanceCents, 0);
      const transfers = suggestTransfers(balances);
      expect(transfers.length).toBeLessThanOrEqual(n - 1);
      const after = new Map(balances.map((b) => [b.memberId, b.balanceCents]));
      for (const t of transfers) {
        expect(t.amountCents).toBeGreaterThan(0);
        after.set(t.fromMemberId, after.get(t.fromMemberId)! + t.amountCents);
        after.set(t.toMemberId, after.get(t.toMemberId)! - t.amountCents);
      }
      expect([...after.values()].every((v) => v === 0)).toBe(true);
    }
  });
});
