/**
 * JSON shapes of the Split REST API, shared by the server and the web app.
 * The Android and iOS apps mirror these in Kotlin and Swift; see docs/api.md.
 */
import type { TeamBalance, Transfer } from "./balances";
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

/** The sports a team can play; `overig` covers everything else. */
export const SPORTS = [
  "voetbal",
  "hockey",
  "korfbal",
  "volleybal",
  "handbal",
  "basketbal",
  "tennis",
  "padel",
  "rugby",
  "waterpolo",
  "overig",
] as const;
export type Sport = (typeof SPORTS)[number];

export const SPORT_LABELS: Record<Sport, string> = {
  voetbal: "Voetbal",
  hockey: "Hockey",
  korfbal: "Korfbal",
  volleybal: "Volleybal",
  handbal: "Handbal",
  basketbal: "Basketbal",
  tennis: "Tennis",
  padel: "Padel",
  rugby: "Rugby",
  waterpolo: "Waterpolo",
  overig: "Overig",
};

/** The season a date falls in, running from July to June: "2026/27". */
export function seasonOf(date: Date): string {
  const start = date.getMonth() >= 6 ? date.getFullYear() : date.getFullYear() - 1;
  return `${start}/${String((start + 1) % 100).padStart(2, "0")}`;
}

export interface Member {
  id: string;
  name: string;
  /** `pot` is the team pot (teamkas): one per team, never a person. */
  kind: "player" | "pot";
  /** Null for a member that was added by name and has no account (yet). */
  userId: string | null;
  /** IBAN of the linked account, so others know where to pay. */
  iban: string | null;
}

/**
 * The eight highlighter colours of the house style (see docs/design.md). A
 * group stores one; a member's colour is their position in the group, in this order.
 */
export const GROUP_COLORS = ["amber", "rose", "green", "coral", "blue", "lime", "teal", "slate"] as const;
export type GroupColor = (typeof GROUP_COLORS)[number];

export interface GroupSummary {
  id: string;
  name: string;
  color: GroupColor;
  sport: Sport;
  club: string | null;
  season: string | null;
  currency: string;
  /** Team members, not counting the pot. */
  memberCount: number;
  myMemberId: string;
  myBalanceCents: number;
  lastActivityAt: string;
}

/** A line of the team's fine list (boetelijst). */
export interface Fine {
  id: string;
  name: string;
  amountCents: number;
}

export interface FineStat {
  memberId: string;
  count: number;
  totalCents: number;
}

export interface PotStatus {
  /** Money actually in the pot (kasstand). */
  cashCents: number;
  /** What members still have to pay into the pot. */
  toReceiveCents: number;
  /** What the pot still has to pay out to members who advanced something. */
  toPayCents: number;
}

export interface GroupDetail {
  id: string;
  name: string;
  color: GroupColor;
  sport: Sport;
  club: string | null;
  season: string | null;
  currency: string;
  inviteCode: string;
  myMemberId: string;
  /** The team pot, also listed last in `members`. */
  potMemberId: string;
  /** The member who keeps the pot's money; their IBAN is where contributions go. */
  treasurerMemberId: string | null;
  /** Team members in the order they joined, followed by the pot. */
  members: Member[];
  balances: TeamBalance[];
  /** Suggested payments: first everyone with the pot, then members among themselves. */
  transfers: Transfer[];
  pot: PotStatus;
  fines: Fine[];
  /** Fines per member, most first; only members with at least one fine. */
  fineStats: FineStat[];
  totalSpentCents: number;
}

/**
 * `fine` and `contribution` are charges: the pot is the payer and every listed
 * member owes it the amount. No money moves until they pay the pot.
 */
export type ExpenseKind = "expense" | "settlement" | "fine" | "contribution";

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

export interface ChargeInput {
  type: "fine" | "contribution";
  description: string;
  /** The amount per member. */
  amountCents: number;
  memberIds: string[];
  date?: string;
}

export interface FineInput {
  name: string;
  amountCents: number;
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
  sport: Sport;
  club: string | null;
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
