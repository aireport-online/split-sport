import { describe, expect, it } from "vitest";
import {
  computeBalances,
  computeShares,
  computeTeamBalances,
  potCashCents,
  suggestTeamTransfers,
  suggestTransfers,
  type LedgerEntry,
  type PotLedgerEntry,
} from "../src";

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

describe("a team with a pot", () => {
  const team = ["anna", "bas", "chris", "pot"];
  const entries: PotLedgerEntry[] = [
    // Everyone is asked to put 10,00 in the pot.
    { kind: "contribution", amountCents: 3000, paidById: "pot", shares: ["anna", "bas", "chris"].map((memberId) => ({ memberId, amountCents: 1000 })) },
    // Bas was late: a fine of 2,00.
    { kind: "fine", amountCents: 200, paidById: "pot", shares: [{ memberId: "bas", amountCents: 200 }] },
    // Anna pays her contribution.
    { kind: "settlement", amountCents: 1000, paidById: "anna", shares: [{ memberId: "pot", amountCents: 1000 }] },
    // Chris buys match balls for the team; the pot will pay him back.
    { kind: "expense", amountCents: 4000, paidById: "chris", shares: [{ memberId: "pot", amountCents: 4000 }] },
    // The pot pays the referee in cash.
    { kind: "expense", amountCents: 600, paidById: "pot", shares: [{ memberId: "pot", amountCents: 600 }] },
    // Anna paid for drinks after the match, shared by the three of them.
    { kind: "expense", amountCents: 3000, paidById: "anna", shares: ["anna", "bas", "chris"].map((memberId) => ({ memberId, amountCents: 1000 })) },
  ];

  it("separates what members have with the pot from what they have with each other", () => {
    const balances = computeTeamBalances(team, "pot", entries);
    expect(balances).toEqual([
      { memberId: "anna", balanceCents: 2000, potCents: 0, peerCents: 2000 },
      { memberId: "bas", balanceCents: -2200, potCents: -1200, peerCents: -1000 },
      { memberId: "chris", balanceCents: 2000, potCents: 3000, peerCents: -1000 },
      { memberId: "pot", balanceCents: -1800, potCents: -1800, peerCents: 0 },
    ]);
    expect(balances.map((b) => b.balanceCents)).toEqual(computeBalances(team, entries).map((b) => b.balanceCents));
    expect(balances.reduce((sum, b) => sum + b.balanceCents, 0)).toBe(0);
  });

  it("settles with the pot first, then among members", () => {
    const balances = computeTeamBalances(team, "pot", entries);
    expect(suggestTeamTransfers(balances, "pot")).toEqual([
      { fromMemberId: "bas", toMemberId: "pot", amountCents: 1200 },
      { fromMemberId: "pot", toMemberId: "chris", amountCents: 3000 },
      { fromMemberId: "bas", toMemberId: "anna", amountCents: 1000 },
      { fromMemberId: "chris", toMemberId: "anna", amountCents: 1000 },
    ]);
  });

  it("counts only money that really moved as cash in the pot", () => {
    // 10,00 came in from Anna, 6,00 went to the referee.
    expect(potCashCents("pot", entries)).toBe(400);
    const paidOut: PotLedgerEntry = { kind: "settlement", amountCents: 300, paidById: "pot", shares: [{ memberId: "chris", amountCents: 300 }] };
    expect(potCashCents("pot", [...entries, paidOut])).toBe(100);
    expect(potCashCents("pot", [])).toBe(0);
  });
});
