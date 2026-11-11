import {
  SplitError,
  computeShares,
  parseAmount,
  splitErrorMessages,
  type Expense,
  type ExpenseInput,
  type GroupDetail,
  type Member,
  type ReceiptScan,
  type Share,
  type Split,
  type SplitMode,
} from "@split/core";
import { money, todayIso } from "../lib/format";

export interface ItemDraft {
  key: string;
  name: string;
  amount: string;
  memberIds: string[];
}

/**
 * What the expense form holds while someone is typing. Every split mode keeps
 * its own state, so switching between modes never loses input.
 */
export interface ExpenseDraft {
  description: string;
  amount: string;
  date: string;
  paidById: string;
  mode: SplitMode;
  equalIds: string[];
  exact: Record<string, string>;
  weights: Record<string, number>;
  items: ItemDraft[];
}

let nextKey = 0;
export const newItem = (name = "", amount = "", memberIds: string[] = []): ItemDraft => ({
  key: `item-${nextKey++}`,
  name,
  amount,
  memberIds,
});

/** "12,50" for an input field. */
export const amountText = (cents: number): string => (cents / 100).toFixed(2).replace(".", ",");

/** A new expense is shared by the team members; the pot only takes part when someone ticks it. */
export function newDraft(group: GroupDetail): ExpenseDraft {
  return {
    description: "",
    amount: "",
    date: todayIso(),
    paidById: group.myMemberId,
    mode: "equal",
    equalIds: group.members.filter((m) => m.kind === "player").map((m) => m.id),
    exact: {},
    weights: Object.fromEntries(group.members.map((m) => [m.id, m.kind === "player" ? 1 : 0])),
    items: [newItem()],
  };
}

/** The tax-free rate per kilometre that most teams use for driving to away games. */
export const DEFAULT_RATE_PER_KM = "0,23";

/**
 * Fills in the amount of a ride from its distance and a rate per kilometre,
 * and names the expense when it has no description yet. Returns null while
 * either number is not usable.
 */
export function applyRide(draft: ExpenseDraft, kilometres: string, ratePerKm: string): ExpenseDraft | null {
  const km = Number(kilometres.trim().replace(",", "."));
  const rateCents = parseAmount(ratePerKm);
  if (!Number.isFinite(km) || km <= 0 || rateCents === null || rateCents <= 0) return null;
  const amountCents = Math.round(km * rateCents);
  if (amountCents <= 0) return null;
  return {
    ...draft,
    amount: amountText(amountCents),
    description: draft.description.trim() || `Rit (${String(km).replace(".", ",")} km)`,
  };
}

export function draftFromExpense(expense: Expense, group: GroupDetail): ExpenseDraft {
  const draft = newDraft(group);
  draft.description = expense.description;
  draft.amount = amountText(expense.amountCents);
  draft.date = expense.date;
  draft.paidById = expense.paidById;
  draft.mode = expense.splitMode;
  const shareOf = new Map(expense.shares.map((s) => [s.memberId, s]));
  if (expense.splitMode === "equal") draft.equalIds = expense.shares.map((s) => s.memberId);
  if (expense.splitMode === "exact") {
    draft.exact = Object.fromEntries(expense.shares.map((s) => [s.memberId, amountText(s.amountCents)]));
  }
  if (expense.splitMode === "shares") {
    draft.weights = Object.fromEntries(group.members.map((m) => [m.id, shareOf.get(m.id)?.weight ?? 0]));
  }
  if (expense.splitMode === "items") {
    draft.items = expense.items.map((item) => newItem(item.name, amountText(item.amountCents), item.memberIds));
  }
  return draft;
}

/** Fills the form from a scanned receipt; nobody is assigned to the lines yet. */
export function applyScan(draft: ExpenseDraft, scan: ReceiptScan): ExpenseDraft {
  const itemsTotal = scan.items.reduce((sum, item) => sum + item.amountCents, 0);
  return {
    ...draft,
    mode: "items",
    description: draft.description.trim() || scan.merchant || "",
    amount: amountText(scan.totalCents ?? itemsTotal),
    date: scan.date ?? draft.date,
    items: scan.items.map((item) => newItem(item.name, amountText(item.amountCents))),
  };
}

export interface DraftResult {
  /** Ready to send, or null while the form is incomplete. */
  input: ExpenseInput | null;
  /** Live preview of what everyone owes; empty while it cannot be computed. */
  shares: Share[];
  /** Why the form cannot be saved yet, in Dutch. */
  problem: string | null;
  /** A neutral remark, e.g. about a tip that will be spread. */
  note: string | null;
}

const filledItems = (draft: ExpenseDraft) =>
  draft.items.filter((item) => item.name.trim() !== "" || item.amount.trim() !== "");

/** Sum of the receipt lines that have a valid amount. */
export const itemsTotalCents = (draft: ExpenseDraft): number =>
  filledItems(draft).reduce((sum, item) => sum + (parseAmount(item.amount) ?? 0), 0);

function buildSplit(draft: ExpenseDraft, members: Member[]): Split {
  // Always in group order, so the preview and the server round the same way.
  const ordered = members.map((m) => m.id);
  switch (draft.mode) {
    case "equal":
      return { mode: "equal", memberIds: ordered.filter((id) => draft.equalIds.includes(id)) };
    case "exact":
      return {
        mode: "exact",
        amounts: ordered
          .map((memberId) => ({ memberId, amountCents: parseAmount(draft.exact[memberId] ?? "") ?? 0 }))
          .filter((entry) => entry.amountCents > 0),
      };
    case "shares":
      return {
        mode: "shares",
        weights: ordered.map((memberId) => ({ memberId, weight: draft.weights[memberId] ?? 0 })),
      };
    case "items":
      return {
        mode: "items",
        items: filledItems(draft).map((item, index) => ({
          name: item.name.trim() || `Regel ${index + 1}`,
          amountCents: parseAmount(item.amount) ?? 0,
          memberIds: ordered.filter((id) => item.memberIds.includes(id)),
        })),
      };
  }
}

export function evaluateDraft(draft: ExpenseDraft, members: Member[], currency = "EUR"): DraftResult {
  const amountCents = parseAmount(draft.amount);
  const split = buildSplit(draft, members);
  const result: DraftResult = { input: null, shares: [], problem: null, note: null };

  if (amountCents === null || amountCents <= 0) {
    result.problem = draft.amount.trim() === "" ? "Vul het bedrag in." : splitErrorMessages.invalid_amount;
    return result;
  }

  if (split.mode === "exact") {
    const divided = split.amounts.reduce((sum, entry) => sum + entry.amountCents, 0);
    if (divided < amountCents) result.problem = `Nog ${money(amountCents - divided, currency)} te verdelen.`;
    if (divided > amountCents) result.problem = `${money(divided - amountCents, currency)} te veel verdeeld.`;
    if (result.problem) return result;
  }

  try {
    result.shares = computeShares(amountCents, split);
  } catch (error) {
    if (!(error instanceof SplitError)) throw error;
    result.problem = splitErrorMessages[error.code];
    return result;
  }

  if (split.mode === "items") {
    const difference = amountCents - itemsTotalCents(draft);
    if (difference !== 0) result.note = `Verschil van ${money(difference, currency)} wordt naar rato verdeeld.`;
  }

  if (draft.description.trim() === "") {
    result.problem = "Geef de uitgave een omschrijving.";
    return result;
  }

  result.input = {
    description: draft.description.trim(),
    amountCents,
    date: draft.date,
    paidById: draft.paidById,
    split,
  };
  return result;
}
