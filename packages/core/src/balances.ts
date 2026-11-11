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

// ---- teams with a pot (teamkas) ---------------------------------------------

/** An entry with the fields needed to follow the team pot. */
export interface PotLedgerEntry extends LedgerEntry {
  kind: "expense" | "settlement" | "fine" | "contribution";
}

export interface TeamBalance extends Balance {
  /** The part of the balance that is with the team pot. Positive: the pot owes this member. */
  potCents: number;
  /** The part that is between members. Positive: other members owe this member. */
  peerCents: number;
}

/**
 * Splits every member's balance in what they have with the team pot and what
 * they have with each other. Every share is a debt of the sharer to the payer;
 * it belongs to the pot ledger when either of the two is the pot. For the pot
 * itself `potCents` is its whole balance: positive when members still owe it.
 */
export function computeTeamBalances(memberIds: string[], potId: string, entries: LedgerEntry[]): TeamBalance[] {
  const pot = new Map<string, number>(memberIds.map((id) => [id, 0]));
  const peer = new Map<string, number>(memberIds.map((id) => [id, 0]));
  const add = (map: Map<string, number>, id: string, cents: number) => map.set(id, (map.get(id) ?? 0) + cents);

  for (const entry of entries) {
    for (const share of entry.shares) {
      if (share.memberId === entry.paidById) continue;
      if (entry.paidById === potId) {
        add(pot, share.memberId, -share.amountCents);
      } else if (share.memberId === potId) {
        add(pot, entry.paidById, share.amountCents);
      } else {
        add(peer, entry.paidById, share.amountCents);
        add(peer, share.memberId, -share.amountCents);
      }
    }
  }

  let owedToPot = 0;
  for (const [id, cents] of pot) if (id !== potId) owedToPot -= cents;
  pot.set(potId, owedToPot);

  return memberIds.map((memberId) => {
    const potCents = pot.get(memberId) ?? 0;
    const peerCents = memberId === potId ? 0 : (peer.get(memberId) ?? 0);
    return { memberId, balanceCents: potCents + peerCents, potCents, peerCents };
  });
}

/**
 * Payments that settle a team: first everyone squares up with the pot
 * directly (so the treasurer sees every contribution and fine come in), then
 * the members settle what is left among themselves with as few payments as possible.
 */
export function suggestTeamTransfers(balances: TeamBalance[], potId: string): Transfer[] {
  const members = balances.filter((b) => b.memberId !== potId);
  const withPot: Transfer[] = [];
  for (const b of members) {
    if (b.potCents < 0) withPot.push({ fromMemberId: b.memberId, toMemberId: potId, amountCents: -b.potCents });
    if (b.potCents > 0) withPot.push({ fromMemberId: potId, toMemberId: b.memberId, amountCents: b.potCents });
  }
  const amongMembers = suggestTransfers(members.map((b) => ({ memberId: b.memberId, balanceCents: b.peerCents })));
  return [...withPot, ...amongMembers];
}

/**
 * The money that is actually in the pot: what was paid in, minus what was paid
 * out and what the pot spent. Fines and contributions are claims, not cash, so
 * they only count once someone pays them.
 */
export function potCashCents(potId: string, entries: PotLedgerEntry[]): number {
  let cash = 0;
  for (const entry of entries) {
    if (entry.kind === "settlement") {
      if (entry.paidById === potId) cash -= entry.amountCents;
      for (const share of entry.shares) if (share.memberId === potId) cash += share.amountCents;
    } else if (entry.kind === "expense" && entry.paidById === potId) {
      cash -= entry.amountCents;
    }
  }
  return cash;
}
