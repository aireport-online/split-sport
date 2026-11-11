import type { Expense, GroupDetail } from "@split/core";
import { describe, expect, it } from "vitest";
import { applyRide, applyScan, draftFromExpense, evaluateDraft, newDraft, newItem } from "../src/expense/draft";

const group: GroupDetail = {
  id: "g",
  name: "Huis",
  color: "blue",
  sport: "voetbal",
  club: null,
  season: "2026/27",
  currency: "EUR",
  inviteCode: "ABCDEFGH",
  myMemberId: "a",
  potMemberId: "pot",
  treasurerMemberId: "a",
  members: [
    { id: "a", name: "Anna", kind: "player", userId: "u1", iban: null },
    { id: "b", name: "Bas", kind: "player", userId: "u2", iban: null },
    { id: "c", name: "Chris", kind: "player", userId: null, iban: null },
    { id: "pot", name: "Teamkas", kind: "pot", userId: null, iban: null },
  ],
  balances: [],
  transfers: [],
  pot: { cashCents: 0, toReceiveCents: 0, toPayCents: 0 },
  fines: [],
  fineStats: [],
  totalSpentCents: 0,
};

describe("expense draft", () => {
  it("starts as an equal split over the team members, paid by you", () => {
    const draft = { ...newDraft(group), description: "Boodschappen", amount: "10" };
    const result = evaluateDraft(draft, group.members);
    expect(result.problem).toBeNull();
    expect(result.shares.map((s) => s.amountCents)).toEqual([334, 333, 333]);
    expect(result.input).toMatchObject({
      description: "Boodschappen",
      amountCents: 1000,
      paidById: "a",
      split: { mode: "equal", memberIds: ["a", "b", "c"] },
    });
  });

  it("explains what is missing", () => {
    const draft = newDraft(group);
    expect(evaluateDraft(draft, group.members).problem).toBe("Vul het bedrag in.");
    expect(evaluateDraft({ ...draft, amount: "abc" }, group.members).problem).toBe("Vul een geldig bedrag in.");
    expect(evaluateDraft({ ...draft, amount: "5" }, group.members).problem).toBe("Geef de uitgave een omschrijving.");
    expect(evaluateDraft({ ...draft, amount: "5", description: "x", equalIds: [] }, group.members).problem).toBe(
      "Kies minstens één deelnemer.",
    );
  });

  it("tells how much is left to divide with exact amounts", () => {
    const draft = { ...newDraft(group), description: "Taxi", amount: "30,00", mode: "exact" as const };
    expect(evaluateDraft({ ...draft, exact: { a: "10" } }, group.members).problem).toBe("Nog € 20,00 te verdelen.");
    expect(evaluateDraft({ ...draft, exact: { a: "10", b: "25" } }, group.members).problem).toBe("€ 5,00 te veel verdeeld.");
    const ok = evaluateDraft({ ...draft, exact: { a: "10", b: "20,00" } }, group.members);
    expect(ok.input?.split).toEqual({
      mode: "exact",
      amounts: [
        { memberId: "a", amountCents: 1000 },
        { memberId: "b", amountCents: 2000 },
      ],
    });
  });

  it("splits receipt lines and notes a tip", () => {
    const draft = {
      ...newDraft(group),
      description: "Café",
      amount: "44",
      mode: "items" as const,
      items: [newItem("Steak", "30,00", ["a"]), newItem("Salade", "10", ["b"]), newItem()],
    };
    const result = evaluateDraft(draft, group.members);
    expect(result.shares).toEqual([
      { memberId: "a", amountCents: 3300 },
      { memberId: "b", amountCents: 1100 },
    ]);
    expect(result.note).toBe("Verschil van € 4,00 wordt naar rato verdeeld.");
    expect(result.input?.split).toMatchObject({ mode: "items", items: [{ name: "Steak" }, { name: "Salade" }] });

    const unassigned = evaluateDraft({ ...draft, items: [newItem("Steak", "30", [])] }, group.members);
    expect(unassigned.problem).toBe("Wijs elke regel van de bon aan iemand toe.");
  });

  it("prefills from a scanned receipt", () => {
    const draft = applyScan(newDraft(group), {
      merchant: "Café De Zon",
      date: "2026-11-01",
      totalCents: null,
      items: [
        { name: "2× Bier", amountCents: 900 },
        { name: "Bitterballen", amountCents: 1250 },
      ],
    });
    expect(draft).toMatchObject({ mode: "items", description: "Café De Zon", amount: "21,50", date: "2026-11-01" });
    expect(draft.items.map((i) => [i.name, i.amount, i.memberIds])).toEqual([
      ["2× Bier", "9,00", []],
      ["Bitterballen", "12,50", []],
    ]);
  });

  it("rebuilds the form from a stored expense", () => {
    const expense: Expense = {
      id: "e",
      groupId: "g",
      kind: "expense",
      description: "Huisje",
      amountCents: 9000,
      paidById: "b",
      date: "2026-10-01",
      splitMode: "shares",
      shares: [
        { memberId: "a", amountCents: 6000, weight: 2 },
        { memberId: "b", amountCents: 3000, weight: 1 },
      ],
      items: [],
      createdAt: "",
      updatedAt: "",
    };
    const draft = draftFromExpense(expense, group);
    expect(draft).toMatchObject({ amount: "90,00", paidById: "b", mode: "shares", weights: { a: 2, b: 1, c: 0, pot: 0 } });
    expect(evaluateDraft(draft, group.members).shares.map((s) => s.amountCents)).toEqual([6000, 3000]);
  });

  it("charges an expense to the pot when only the pot is ticked", () => {
    const draft = { ...newDraft(group), description: "Scheidsrechter", amount: "30", paidById: "pot", equalIds: ["pot"] };
    const result = evaluateDraft(draft, group.members);
    expect(result.shares).toEqual([{ memberId: "pot", amountCents: 3000 }]);
    expect(result.input).toMatchObject({ paidById: "pot", split: { mode: "equal", memberIds: ["pot"] } });
  });

  it("works out a ride from kilometres and a rate", () => {
    const ride = applyRide(newDraft(group), "84", "0,23");
    expect(ride).toMatchObject({ amount: "19,32", description: "Rit (84 km)" });
    expect(applyRide({ ...newDraft(group), description: "Naar Zwolle" }, "12,5", "0,30")).toMatchObject({
      amount: "3,75",
      description: "Naar Zwolle",
    });
    expect(applyRide(newDraft(group), "", "0,23")).toBeNull();
    expect(applyRide(newDraft(group), "84", "nul")).toBeNull();
    expect(applyRide(newDraft(group), "-5", "0,23")).toBeNull();
  });
});
