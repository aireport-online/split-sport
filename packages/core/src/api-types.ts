/**
 * JSON shapes of the Split REST API, shared by the server and the web app.
 * The Android and iOS apps mirror these in Kotlin and Swift; see docs/api.md.
 */
import type { Balance, Transfer } from "./balances";
import type { Share, Split, SplitMode } from "./split";

export interface ApiError {
  error: { code: string; message: string };
}

export interface ServerConfig {
  receiptScanEnabled: boolean;
}

export interface User {
  id: string;
  name: string;
  email: string;
  iban: string | null;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Member {
  id: string;
  name: string;
  /** Null for a member that was added by name and has no account (yet). */
  userId: string | null;
  /** IBAN of the linked account, so others know where to pay. */
  iban: string | null;
}

/**
 * Groups have no picture; every client draws a tile in one of these tints
 * with the group's initials. Members get a tint by their position in the group.
 */
export const GROUP_COLORS = ["blue", "amber", "teal", "rose", "green", "coral", "lime", "slate"] as const;
export type GroupColor = (typeof GROUP_COLORS)[number];

export interface GroupSummary {
  id: string;
  name: string;
  color: GroupColor;
  currency: string;
  memberCount: number;
  myMemberId: string;
  myBalanceCents: number;
  lastActivityAt: string;
}

export interface GroupDetail {
  id: string;
  name: string;
  color: GroupColor;
  currency: string;
  inviteCode: string;
  myMemberId: string;
  members: Member[];
  balances: Balance[];
  /** Suggested payments that settle every balance. */
  transfers: Transfer[];
  totalSpentCents: number;
}

export type ExpenseKind = "expense" | "settlement";

export interface ExpenseItem {
  name: string;
  amountCents: number;
  memberIds: string[];
}

export interface Expense {
  id: string;
  groupId: string;
  kind: ExpenseKind;
  description: string;
  amountCents: number;
  /** Member that paid. For a settlement: the member that paid back. */
  paidById: string;
  /** Calendar date, YYYY-MM-DD. */
  date: string;
  splitMode: SplitMode;
  /** What every participant owes. For a settlement: one share for the receiver. */
  shares: (Share & { weight: number | null })[];
  /** Receipt lines, only for splitMode "items". */
  items: ExpenseItem[];
  createdAt: string;
  updatedAt: string;
}

export interface ExpenseInput {
  description: string;
  amountCents: number;
  date: string;
  paidById: string;
  split: Split;
}

export interface SettlementInput {
  fromMemberId: string;
  toMemberId: string;
  amountCents: number;
  date?: string;
}

export interface InvitePreview {
  groupId: string;
  name: string;
  color: GroupColor;
  memberCount: number;
  alreadyMember: boolean;
  /** Members without an account that the invitee may claim as themselves. */
  unclaimedMembers: { id: string; name: string }[];
}

export interface ReceiptScan {
  merchant: string | null;
  date: string | null;
  totalCents: number | null;
  items: { name: string; amountCents: number }[];
}
