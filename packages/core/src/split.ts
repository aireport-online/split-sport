/**
 * Splitting an amount of money over group members.
 *
 * All amounts are integer cents. The same algorithm is implemented in the
 * Android (Kotlin) and iOS (Swift) apps for live previews; `spec/split-vectors.json`
 * holds the shared test vectors that keep the three implementations identical.
 * The server result is always authoritative.
 */

export const MAX_AMOUNT_CENTS = 99_999_999;
export const MAX_WEIGHT = 1_000_000;

export type Split =
  | { mode: "equal"; memberIds: string[] }
  | { mode: "exact"; amounts: { memberId: string; amountCents: number }[] }
  | { mode: "shares"; weights: { memberId: string; weight: number }[] }
  | { mode: "items"; items: SplitItem[] };

export type SplitMode = Split["mode"];

export interface SplitItem {
  name: string;
  amountCents: number;
  memberIds: string[];
}

export interface Share {
  memberId: string;
  amountCents: number;
}

export type SplitErrorCode =
  | "invalid_amount"
  | "no_participants"
  | "duplicate_member"
  | "invalid_weight"
  | "exact_sum_mismatch"
  | "invalid_item"
  | "item_unassigned";

export class SplitError extends Error {
  constructor(readonly code: SplitErrorCode) {
    super(code);
    this.name = "SplitError";
  }
}

/**
 * Distributes `totalCents` proportionally to `weights` using the largest
 * remainder method. Leftover cents go to the largest fractional parts; ties go
 * to the earlier index. The result always sums to exactly `totalCents`.
 * A negative total is allocated as its absolute value and negated.
 */
export function allocate(totalCents: number, weights: number[]): number[] {
  const weightSum = weights.reduce((a, b) => a + b, 0);
  if (weights.length === 0 || weightSum <= 0) throw new SplitError("no_participants");
  if (totalCents < 0) return allocate(-totalCents, weights).map((v) => (v === 0 ? 0 : -v));

  const total = BigInt(totalCents);
  const sum = BigInt(weightSum);
  const result: number[] = [];
  const remainders: { index: number; remainder: bigint }[] = [];
  let allocated = 0;
  weights.forEach((weight, index) => {
    const product = total * BigInt(weight);
    const base = Number(product / sum);
    result.push(base);
    allocated += base;
    remainders.push({ index, remainder: product % sum });
  });
  remainders.sort((a, b) =>
    a.remainder === b.remainder ? a.index - b.index : a.remainder > b.remainder ? -1 : 1,
  );
  let leftover = totalCents - allocated;
  for (const { index } of remainders) {
    if (leftover <= 0) break;
    result[index] = result[index]! + 1;
    leftover -= 1;
  }
  return result;
}

function isCents(value: number): boolean {
  return Number.isSafeInteger(value);
}

function assertUnique(ids: string[]): void {
  if (new Set(ids).size !== ids.length) throw new SplitError("duplicate_member");
}

/**
 * Computes what every participant owes for an expense of `amountCents`.
 * Shares are returned in order of first appearance and sum to `amountCents`.
 */
export function computeShares(amountCents: number, split: Split): Share[] {
  if (!isCents(amountCents) || amountCents <= 0 || amountCents > MAX_AMOUNT_CENTS) {
    throw new SplitError("invalid_amount");
  }

  switch (split.mode) {
    case "equal": {
      if (split.memberIds.length === 0) throw new SplitError("no_participants");
      assertUnique(split.memberIds);
      const parts = allocate(
        amountCents,
        split.memberIds.map(() => 1),
      );
      return split.memberIds.map((memberId, i) => ({ memberId, amountCents: parts[i]! }));
    }

    case "exact": {
      if (split.amounts.length === 0) throw new SplitError("no_participants");
      assertUnique(split.amounts.map((a) => a.memberId));
      let sum = 0;
      for (const entry of split.amounts) {
        if (!isCents(entry.amountCents) || entry.amountCents < 0) throw new SplitError("invalid_amount");
        sum += entry.amountCents;
      }
      if (sum !== amountCents) throw new SplitError("exact_sum_mismatch");
      return split.amounts
        .filter((a) => a.amountCents > 0)
        .map((a) => ({ memberId: a.memberId, amountCents: a.amountCents }));
    }

    case "shares": {
      assertUnique(split.weights.map((w) => w.memberId));
      for (const entry of split.weights) {
        if (!Number.isInteger(entry.weight) || entry.weight < 0 || entry.weight > MAX_WEIGHT) {
          throw new SplitError("invalid_weight");
        }
      }
      const active = split.weights.filter((w) => w.weight > 0);
      if (active.length === 0) throw new SplitError("no_participants");
      const parts = allocate(
        amountCents,
        active.map((w) => w.weight),
      );
      return active.map((w, i) => ({ memberId: w.memberId, amountCents: parts[i]! }));
    }

    case "items": {
      if (split.items.length === 0) throw new SplitError("no_participants");
      const order: string[] = [];
      const subtotals = new Map<string, number>();
      let itemsTotal = 0;
      for (const item of split.items) {
        if (!isCents(item.amountCents) || item.amountCents <= 0 || item.amountCents > MAX_AMOUNT_CENTS) {
          throw new SplitError("invalid_item");
        }
        if (item.memberIds.length === 0) throw new SplitError("item_unassigned");
        assertUnique(item.memberIds);
        itemsTotal += item.amountCents;
        const parts = allocate(
          item.amountCents,
          item.memberIds.map(() => 1),
        );
        item.memberIds.forEach((memberId, i) => {
          if (!subtotals.has(memberId)) {
            subtotals.set(memberId, 0);
            order.push(memberId);
          }
          subtotals.set(memberId, subtotals.get(memberId)! + parts[i]!);
        });
      }
      if (itemsTotal > MAX_AMOUNT_CENTS) throw new SplitError("invalid_item");
      // Whatever the receipt total differs from the items (tip, service charge,
      // discount) is spread in proportion to what everyone ordered.
      const weights = order.map((id) => subtotals.get(id)!);
      const extra = allocate(amountCents - itemsTotal, weights);
      return order.map((memberId, i) => ({ memberId, amountCents: weights[i]! + extra[i]! }));
    }
  }
}
