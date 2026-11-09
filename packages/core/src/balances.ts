import type { Share } from "./split";

/** The minimal shape of an expense or settlement needed to compute balances. */
export interface LedgerEntry {
  amountCents: number;
  paidById: string;
  shares: Share[];
}

export interface Balance {
  memberId: string;
  /** Positive: the group owes this member. Negative: this member owes the group. */
  balanceCents: number;
}

export interface Transfer {
  fromMemberId: string;
  toMemberId: string;
  amountCents: number;
}

/**
 * Net balance per member: everything they paid minus everything they owe.
 * A settlement is an entry paid by the debtor with a single share for the
 * creditor, so it needs no special handling. Balances always sum to zero.
 */
export function computeBalances(memberIds: string[], entries: LedgerEntry[]): Balance[] {
  const net = new Map<string, number>(memberIds.map((id) => [id, 0]));
  const add = (id: string, cents: number) => net.set(id, (net.get(id) ?? 0) + cents);
  for (const entry of entries) {
    add(entry.paidById, entry.amountCents);
    for (const share of entry.shares) add(share.memberId, -share.amountCents);
  }
  return [...net].map(([memberId, balanceCents]) => ({ memberId, balanceCents }));
}

/**
 * Suggests who should pay whom to bring every balance to zero, repeatedly
 * matching the largest debtor with the largest creditor. This needs at most
 * n-1 payments. Ties are broken by the order of `balances`, so the result is
 * deterministic.
 */
export function suggestTransfers(balances: Balance[]): Transfer[] {
  const open = balances.map((b, index) => ({ ...b, index }));
  const byAmount = (a: (typeof open)[number], b: (typeof open)[number]) =>
    Math.abs(b.balanceCents) - Math.abs(a.balanceCents) || a.index - b.index;

  const transfers: Transfer[] = [];
  for (;;) {
    const debtor = open.filter((b) => b.balanceCents < 0).sort(byAmount)[0];
    const creditor = open.filter((b) => b.balanceCents > 0).sort(byAmount)[0];
    if (!debtor || !creditor) break;
    const amountCents = Math.min(-debtor.balanceCents, creditor.balanceCents);
    transfers.push({ fromMemberId: debtor.memberId, toMemberId: creditor.memberId, amountCents });
    debtor.balanceCents += amountCents;
    creditor.balanceCents -= amountCents;
  }
  return transfers;
}
