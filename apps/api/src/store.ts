import {
  GROUP_COLORS,
  computeShares,
  computeTeamBalances,
  potCashCents,
  suggestTeamTransfers,
  type ChargeInput,
  type Expense,
  type ExpenseInput,
  type ExpenseItem,
  type ExpenseKind,
  type Fine,
  type FineInput,
  type FineStat,
  type GroupColor,
  type GroupDetail,
  type GroupSummary,
  type InvitePreview,
  type Member,
  type Split,
  type SplitMode,
  type Sport,
  type User,
} from "@split/core";
import { newId, newInviteCode, type UserRow } from "./auth";
import type { Db } from "./db";
import { HttpError, notFound } from "./errors";

export const toUser = (row: UserRow): User => ({ id: row.id, name: row.name, email: row.email, iban: row.iban });

interface GroupRow {
  id: string;
  name: string;
  color: GroupColor;
  sport: Sport;
  club: string | null;
  season: string | null;
  treasurer_member_id: string | null;
  currency: string;
  invite_code: string;
  created_at: string;
}

interface MemberRow {
  id: string;
  group_id: string;
  user_id: string | null;
  name: string;
  kind: "player" | "pot";
}

interface ExpenseRow {
  id: string;
  group_id: string;
  kind: "expense" | "settlement";
  entry_type: "fine" | "contribution" | null;
  description: string;
  amount_cents: number;
  paid_by_member_id: string;
  date: string;
  split_mode: SplitMode;
  created_at: string;
  updated_at: string;
}

const POT_NAME = "Teamkas";

/** The fine list a new team starts with; teams edit it to their own rules. */
const DEFAULT_FINES: FineInput[] = [
  { name: "Te laat", amountCents: 200 },
  { name: "Niet afgemeld", amountCents: 500 },
  { name: "Spullen vergeten", amountCents: 200 },
  { name: "Gele kaart", amountCents: 500 },
  { name: "Rode kaart", amountCents: 1000 },
];

export interface GroupPatch {
  name?: string;
  color?: GroupColor;
  sport?: Sport;
  club?: string | null;
  season?: string | null;
  treasurerMemberId?: string;
}

const BALANCE_SQL = `
  COALESCE((SELECT SUM(e.amount_cents) FROM expenses e WHERE e.paid_by_member_id = m.id), 0)
  - COALESCE((SELECT SUM(s.amount_cents) FROM expense_shares s WHERE s.member_id = m.id), 0)`;

/** All reads and writes of groups, members and expenses. */
export class Store {
  constructor(private readonly db: Db) {}

  // ---- groups -------------------------------------------------------------

  listGroups(userId: string): GroupSummary[] {
    const rows = this.db
      .prepare<
        [string],
        GroupRow & { my_member_id: string; my_balance: number; member_count: number; last_activity: string | null }
      >(
        `SELECT g.*, m.id AS my_member_id, ${BALANCE_SQL} AS my_balance,
                (SELECT COUNT(*) FROM members x WHERE x.group_id = g.id AND x.kind = 'player') AS member_count,
                (SELECT MAX(e.updated_at) FROM expenses e WHERE e.group_id = g.id) AS last_activity
           FROM members m JOIN groups g ON g.id = m.group_id
          WHERE m.user_id = ?`,
      )
      .all(userId);
    return rows
      .map((row) => ({
        id: row.id,
        name: row.name,
        color: row.color,
        sport: row.sport,
        club: row.club,
        season: row.season,
        currency: row.currency,
        memberCount: row.member_count,
        myMemberId: row.my_member_id,
        myBalanceCents: row.my_balance,
        lastActivityAt: row.last_activity ?? row.created_at,
      }))
      .sort((a, b) => b.lastActivityAt.localeCompare(a.lastActivityAt));
  }

  createGroup(
    user: UserRow,
    input: { name: string; color?: GroupColor; currency: string; sport: Sport; club?: string | null; season?: string | null },
  ): GroupDetail {
    const id = newId();
    const now = new Date().toISOString();
    // Without a chosen colour, cycle through the palette so a user's groups look different.
    const count = this.db
      .prepare<[string], { n: number }>("SELECT COUNT(*) AS n FROM members WHERE user_id = ?")
      .get(user.id)!.n;
    const color = input.color ?? GROUP_COLORS[count % GROUP_COLORS.length]!;
    this.db.transaction(() => {
      this.db
        .prepare(
          `INSERT INTO groups (id, name, color, sport, club, season, currency, invite_code, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        )
        .run(id, input.name, color, input.sport, input.club ?? null, input.season ?? null, input.currency, newInviteCode(), now);
      // The creator starts as treasurer; the pot is created with the team.
      const creator = this.insertMember(id, user.name, user.id);
      this.insertMember(id, POT_NAME, null, "pot");
      this.db.prepare("UPDATE groups SET treasurer_member_id = ? WHERE id = ?").run(creator, id);
      DEFAULT_FINES.forEach((fine) => this.addFine(id, fine));
    })();
    return this.groupDetail(id, user.id);
  }

  updateGroup(groupId: string, patch: GroupPatch): void {
    const set = (column: string, value: string | null) =>
      this.db.prepare(`UPDATE groups SET ${column} = ? WHERE id = ?`).run(value, groupId);
    this.db.transaction(() => {
      if (patch.name !== undefined) set("name", patch.name);
      if (patch.color !== undefined) set("color", patch.color);
      if (patch.sport !== undefined) set("sport", patch.sport);
      if (patch.club !== undefined) set("club", patch.club);
      if (patch.season !== undefined) set("season", patch.season);
      if (patch.treasurerMemberId !== undefined) {
        const member = this.requireMember(groupId, patch.treasurerMemberId);
        if (member.kind === "pot") {
          throw new HttpError(400, "validation_error", "Kies een teamlid als penningmeester.");
        }
        set("treasurer_member_id", member.id);
      }
    })();
  }

  /** The caller's member in a group; throws 404 when they are not in it. */
  requireMembership(groupId: string, userId: string): MemberRow {
    const member = this.db
      .prepare<[string, string], MemberRow>("SELECT * FROM members WHERE group_id = ? AND user_id = ?")
      .get(groupId, userId);
    if (!member) throw notFound("Dit team bestaat niet of je zit er niet in.");
    return member;
  }

  groupDetail(groupId: string, userId: string): GroupDetail {
    const me = this.requireMembership(groupId, userId);
    const group = this.db.prepare<[string], GroupRow>("SELECT * FROM groups WHERE id = ?").get(groupId)!;
    const rows = this.db
      .prepare<[string], MemberRow & { iban: string | null }>(
        `SELECT m.*, u.iban AS iban
           FROM members m LEFT JOIN users u ON u.id = m.user_id
          WHERE m.group_id = ? ORDER BY (m.kind = 'pot'), m.created_at, m.rowid`,
      )
      .all(groupId);
    const members: Member[] = rows.map((r) => ({ id: r.id, name: r.name, kind: r.kind, userId: r.user_id, iban: r.iban }));
    const potId = rows.find((r) => r.kind === "pot")!.id;

    const entries = this.listExpenses(groupId);
    const balances = computeTeamBalances(
      members.map((m) => m.id),
      potId,
      entries,
    );
    const players = balances.filter((b) => b.memberId !== potId);

    return {
      id: group.id,
      name: group.name,
      color: group.color,
      sport: group.sport,
      club: group.club,
      season: group.season,
      currency: group.currency,
      inviteCode: group.invite_code,
      myMemberId: me.id,
      potMemberId: potId,
      treasurerMemberId: group.treasurer_member_id,
      members,
      balances,
      transfers: suggestTeamTransfers(balances, potId),
      pot: {
        cashCents: potCashCents(potId, entries),
        toReceiveCents: players.reduce((sum, b) => sum + Math.max(0, -b.potCents), 0),
        toPayCents: players.reduce((sum, b) => sum + Math.max(0, b.potCents), 0),
      },
      fines: this.listFines(groupId),
      fineStats: Store.fineStats(members, entries),
      totalSpentCents: entries.filter((e) => e.kind === "expense").reduce((sum, e) => sum + e.amountCents, 0),
    };
  }

  /** Who collected the most fines; members without fines are left out. */
  private static fineStats(members: Member[], entries: Expense[]): FineStat[] {
    const stats = new Map<string, FineStat>();
    for (const entry of entries) {
      if (entry.kind !== "fine") continue;
      for (const share of entry.shares) {
        const stat = stats.get(share.memberId) ?? { memberId: share.memberId, count: 0, totalCents: 0 };
        stat.count += 1;
        stat.totalCents += share.amountCents;
        stats.set(share.memberId, stat);
      }
    }
    const order = new Map(members.map((m, index) => [m.id, index]));
    return [...stats.values()].sort(
      (a, b) => b.totalCents - a.totalCents || b.count - a.count || order.get(a.memberId)! - order.get(b.memberId)!,
    );
  }

  // ---- fine list ----------------------------------------------------------

  private listFines(groupId: string): Fine[] {
    return this.db
      .prepare<[string], { id: string; name: string; amount_cents: number }>(
        "SELECT id, name, amount_cents FROM fines WHERE group_id = ? ORDER BY position, rowid",
      )
      .all(groupId)
      .map((row) => ({ id: row.id, name: row.name, amountCents: row.amount_cents }));
  }

  addFine(groupId: string, input: FineInput): void {
    const next = this.db
      .prepare<[string], { next: number }>("SELECT COALESCE(MAX(position), -1) + 1 AS next FROM fines WHERE group_id = ?")
      .get(groupId)!.next;
    this.db
      .prepare("INSERT INTO fines (id, group_id, name, amount_cents, position) VALUES (?, ?, ?, ?, ?)")
      .run(newId(), groupId, input.name, input.amountCents, next);
  }

  updateFine(groupId: string, fineId: string, input: FineInput): void {
    const result = this.db
      .prepare("UPDATE fines SET name = ?, amount_cents = ? WHERE id = ? AND group_id = ?")
      .run(input.name, input.amountCents, fineId, groupId);
    if (result.changes === 0) throw notFound("Deze boete staat niet (meer) op de lijst.");
  }

  removeFine(groupId: string, fineId: string): void {
    const result = this.db.prepare("DELETE FROM fines WHERE id = ? AND group_id = ?").run(fineId, groupId);
    if (result.changes === 0) throw notFound("Deze boete staat niet (meer) op de lijst.");
  }

  // ---- members ------------------------------------------------------------

  private insertMember(groupId: string, name: string, userId: string | null, kind: "player" | "pot" = "player"): string {
    const id = newId();
    this.db
      .prepare("INSERT INTO members (id, group_id, user_id, name, kind, created_at) VALUES (?, ?, ?, ?, ?, ?)")
      .run(id, groupId, userId, name, kind, new Date().toISOString());
    return id;
  }

  addPlaceholderMember(groupId: string, name: string): string {
    return this.insertMember(groupId, name, null);
  }

  private requireMember(groupId: string, memberId: string): MemberRow {
    const member = this.db
      .prepare<[string, string], MemberRow>("SELECT * FROM members WHERE id = ? AND group_id = ?")
      .get(memberId, groupId);
    if (!member) throw notFound("Dit teamlid bestaat niet.");
    return member;
  }

  renameMember(groupId: string, memberId: string, name: string): void {
    const member = this.requireMember(groupId, memberId);
    if (member.user_id || member.kind === "pot") {
      throw new HttpError(403, "forbidden", "Alleen teamleden zonder account kun je hernoemen.");
    }
    this.db.prepare("UPDATE members SET name = ? WHERE id = ?").run(name, memberId);
  }

  private memberHasEntries(memberId: string): boolean {
    const row = this.db
      .prepare<[string, string, string], { used: number }>(
        `SELECT EXISTS (SELECT 1 FROM expenses WHERE paid_by_member_id = ?)
             OR EXISTS (SELECT 1 FROM expense_shares WHERE member_id = ?)
             OR EXISTS (SELECT 1 FROM expense_item_members WHERE member_id = ?) AS used`,
      )
      .get(memberId, memberId, memberId)!;
    return row.used === 1;
  }

  removePlaceholderMember(groupId: string, memberId: string): void {
    const member = this.requireMember(groupId, memberId);
    if (member.kind === "pot") {
      throw new HttpError(403, "forbidden", "De teamkas hoort bij het team en kan niet worden verwijderd.");
    }
    if (member.user_id) {
      throw new HttpError(403, "forbidden", "Teamleden met een account kunnen alleen zelf het team verlaten.");
    }
    if (this.memberHasEntries(memberId)) {
      throw new HttpError(409, "member_in_use", "Dit teamlid staat nog in uitgaven en kan niet worden verwijderd.");
    }
    this.db.prepare("DELETE FROM members WHERE id = ?").run(memberId);
  }

  /**
   * Removes the user from a group. A member with history stays behind as a
   * name-only member so the balances of the others stay correct. A group
   * without any account left is deleted.
   */
  leaveGroup(groupId: string, userId: string): void {
    const member = this.requireMembership(groupId, userId);
    this.db.transaction(() => {
      if (this.memberHasEntries(member.id)) {
        this.db.prepare("UPDATE members SET user_id = NULL WHERE id = ?").run(member.id);
      } else {
        this.db.prepare("DELETE FROM members WHERE id = ?").run(member.id);
      }
      const next = this.db
        .prepare<[string], { id: string }>(
          "SELECT id FROM members WHERE group_id = ? AND user_id IS NOT NULL ORDER BY created_at, rowid LIMIT 1",
        )
        .get(groupId);
      if (!next) {
        this.deleteGroup(groupId);
        return;
      }
      // The pot needs someone to keep it: hand it to the longest-standing member that is left.
      this.db
        .prepare("UPDATE groups SET treasurer_member_id = ? WHERE id = ? AND (treasurer_member_id IS NULL OR treasurer_member_id = ?)")
        .run(next.id, groupId, member.id);
    })();
  }

  private deleteGroup(groupId: string): void {
    // Expenses and the treasurer reference members without cascade, so clear them first.
    this.db.prepare("DELETE FROM expenses WHERE group_id = ?").run(groupId);
    this.db.prepare("UPDATE groups SET treasurer_member_id = NULL WHERE id = ?").run(groupId);
    this.db.prepare("DELETE FROM groups WHERE id = ?").run(groupId);
  }

  deleteAccount(userId: string): void {
    this.db.transaction(() => {
      const groups = this.db.prepare<[string], { group_id: string }>("SELECT group_id FROM members WHERE user_id = ?").all(userId);
      for (const { group_id } of groups) this.leaveGroup(group_id, userId);
      this.db.prepare("DELETE FROM users WHERE id = ?").run(userId);
    })();
  }

  renameUserEverywhere(userId: string, name: string): void {
    this.db.prepare("UPDATE members SET name = ? WHERE user_id = ?").run(name, userId);
  }

  // ---- invites ------------------------------------------------------------

  findGroupByInvite(code: string): GroupRow {
    const group = this.db
      .prepare<[string], GroupRow>("SELECT * FROM groups WHERE invite_code = ?")
      .get(code.trim().toUpperCase());
    if (!group) throw notFound("Deze uitnodigingscode klopt niet.");
    return group;
  }

  invitePreview(code: string, userId: string): InvitePreview {
    const group = this.findGroupByInvite(code);
    const members = this.db
      .prepare<[string], MemberRow>(
        "SELECT * FROM members WHERE group_id = ? AND kind = 'player' ORDER BY created_at, rowid",
      )
      .all(group.id);
    return {
      groupId: group.id,
      name: group.name,
      color: group.color,
      sport: group.sport,
      club: group.club,
      memberCount: members.length,
      alreadyMember: members.some((m) => m.user_id === userId),
      unclaimedMembers: members.filter((m) => m.user_id === null).map((m) => ({ id: m.id, name: m.name })),
    };
  }

  joinGroup(code: string, user: UserRow, claimMemberId: string | undefined): GroupDetail {
    const group = this.findGroupByInvite(code);
    this.db.transaction(() => {
      const existing = this.db
        .prepare("SELECT 1 FROM members WHERE group_id = ? AND user_id = ?")
        .get(group.id, user.id);
      if (existing) return;
      if (claimMemberId) {
        const result = this.db
          .prepare(
            "UPDATE members SET user_id = ?, name = ? WHERE id = ? AND group_id = ? AND user_id IS NULL AND kind = 'player'",
          )
          .run(user.id, user.name, claimMemberId, group.id);
        if (result.changes === 0) {
          throw new HttpError(409, "member_taken", "Dit teamlid is al aan iemand anders gekoppeld.");
        }
      } else {
        this.insertMember(group.id, user.name, user.id);
      }
    })();
    return this.groupDetail(group.id, user.id);
  }

  // ---- expenses -----------------------------------------------------------

  listExpenses(groupId: string): Expense[] {
    const rows = this.db
      .prepare<[string], ExpenseRow>(
        "SELECT * FROM expenses WHERE group_id = ? ORDER BY date DESC, created_at DESC, rowid DESC",
      )
      .all(groupId);
    return this.hydrate(rows);
  }

  getExpense(groupId: string, expenseId: string): Expense {
    const row = this.db
      .prepare<[string, string], ExpenseRow>("SELECT * FROM expenses WHERE id = ? AND group_id = ?")
      .get(expenseId, groupId);
    if (!row) throw notFound("Deze uitgave bestaat niet (meer).");
    return this.hydrate([row])[0]!;
  }

  private hydrate(rows: ExpenseRow[]): Expense[] {
    if (rows.length === 0) return [];
    const ids = rows.map((r) => r.id);
    const marks = ids.map(() => "?").join(",");
    const shares = this.db
      .prepare<string[], { expense_id: string; member_id: string; amount_cents: number; weight: number | null }>(
        `SELECT expense_id, member_id, amount_cents, weight FROM expense_shares
          WHERE expense_id IN (${marks}) ORDER BY position`,
      )
      .all(...ids);
    const items = this.db
      .prepare<string[], { id: string; expense_id: string; name: string; amount_cents: number }>(
        `SELECT id, expense_id, name, amount_cents FROM expense_items WHERE expense_id IN (${marks}) ORDER BY position`,
      )
      .all(...ids);
    const itemMembers = items.length
      ? this.db
          .prepare<string[], { item_id: string; member_id: string }>(
            `SELECT im.item_id, im.member_id FROM expense_item_members im
               JOIN expense_items i ON i.id = im.item_id
              WHERE i.expense_id IN (${marks}) ORDER BY im.position`,
          )
          .all(...ids)
      : [];

    const membersByItem = new Map<string, string[]>();
    for (const row of itemMembers) {
      membersByItem.set(row.item_id, [...(membersByItem.get(row.item_id) ?? []), row.member_id]);
    }
    const itemsByExpense = new Map<string, ExpenseItem[]>();
    for (const row of items) {
      const list = itemsByExpense.get(row.expense_id) ?? [];
      list.push({ name: row.name, amountCents: row.amount_cents, memberIds: membersByItem.get(row.id) ?? [] });
      itemsByExpense.set(row.expense_id, list);
    }
    const sharesByExpense = new Map<string, Expense["shares"]>();
    for (const row of shares) {
      const list = sharesByExpense.get(row.expense_id) ?? [];
      list.push({ memberId: row.member_id, amountCents: row.amount_cents, weight: row.weight });
      sharesByExpense.set(row.expense_id, list);
    }

    return rows.map((row) => ({
      id: row.id,
      groupId: row.group_id,
      kind: row.entry_type ?? row.kind,
      description: row.description,
      amountCents: row.amount_cents,
      paidById: row.paid_by_member_id,
      date: row.date,
      splitMode: row.split_mode,
      shares: sharesByExpense.get(row.id) ?? [],
      items: itemsByExpense.get(row.id) ?? [],
      createdAt: row.created_at,
      updatedAt: row.updated_at,
    }));
  }

  private assertMembersInGroup(groupId: string, memberIds: Iterable<string>): void {
    const known = new Set(
      this.db
        .prepare<[string], { id: string }>("SELECT id FROM members WHERE group_id = ?")
        .all(groupId)
        .map((m) => m.id),
    );
    for (const id of memberIds) {
      if (!known.has(id)) throw new HttpError(400, "unknown_member", "Een van de teamleden hoort niet bij dit team.");
    }
  }

  private static splitMemberIds(split: Split): string[] {
    switch (split.mode) {
      case "equal":
        return split.memberIds;
      case "exact":
        return split.amounts.map((a) => a.memberId);
      case "shares":
        return split.weights.map((w) => w.memberId);
      case "items":
        return split.items.flatMap((i) => i.memberIds);
    }
  }

  /**
   * Charges members for a fine or a contribution to the pot: the pot is the
   * payer and every listed member owes it the amount.
   */
  saveCharge(groupId: string, input: ChargeInput): Expense {
    const pot = this.db
      .prepare<[string], MemberRow>("SELECT * FROM members WHERE group_id = ? AND kind = 'pot'")
      .get(groupId)!;
    if (input.memberIds.includes(pot.id)) {
      throw new HttpError(400, "validation_error", "De teamkas kan zichzelf niets in rekening brengen.");
    }
    const total = input.amountCents * input.memberIds.length;
    return this.saveExpense(groupId, input.type, {
      description: input.description,
      amountCents: total,
      date: input.date ?? new Date().toISOString().slice(0, 10),
      paidById: pot.id,
      split: {
        mode: "exact",
        amounts: input.memberIds.map((memberId) => ({ memberId, amountCents: input.amountCents })),
      },
    });
  }

  /**
   * Creates an expense, settlement, fine or contribution. With `expenseId` it
   * replaces an existing expense; the other kinds are deleted and recorded again.
   */
  saveExpense(groupId: string, kind: ExpenseKind, input: ExpenseInput, expenseId?: string): Expense {
    this.assertMembersInGroup(groupId, [input.paidById, ...Store.splitMemberIds(input.split)]);
    const shares = computeShares(input.amountCents, input.split);
    const weights = new Map(
      input.split.mode === "shares" ? input.split.weights.map((w) => [w.memberId, w.weight]) : [],
    );
    const now = new Date().toISOString();
    const id = expenseId ?? newId();
    const entryType = kind === "fine" || kind === "contribution" ? kind : null;
    const baseKind = kind === "settlement" ? "settlement" : "expense";

    this.db.transaction(() => {
      if (expenseId) {
        const result = this.db
          .prepare(
            `UPDATE expenses SET description = ?, amount_cents = ?, paid_by_member_id = ?, date = ?,
                    split_mode = ?, updated_at = ? WHERE id = ? AND group_id = ? AND kind = ? AND entry_type IS NULL`,
          )
          .run(input.description, input.amountCents, input.paidById, input.date, input.split.mode, now, id, groupId, baseKind);
        if (result.changes === 0) throw notFound("Deze uitgave bestaat niet (meer).");
        this.db.prepare("DELETE FROM expense_shares WHERE expense_id = ?").run(id);
        this.db.prepare("DELETE FROM expense_items WHERE expense_id = ?").run(id);
      } else {
        this.db
          .prepare(
            `INSERT INTO expenses (id, group_id, kind, entry_type, description, amount_cents, paid_by_member_id, date,
                                   split_mode, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          )
          .run(id, groupId, baseKind, entryType, input.description, input.amountCents, input.paidById, input.date, input.split.mode, now, now);
      }

      const insertShare = this.db.prepare(
        "INSERT INTO expense_shares (expense_id, member_id, amount_cents, weight, position) VALUES (?, ?, ?, ?, ?)",
      );
      shares.forEach((share, position) =>
        insertShare.run(id, share.memberId, share.amountCents, weights.get(share.memberId) ?? null, position),
      );

      if (input.split.mode === "items") {
        const insertItem = this.db.prepare(
          "INSERT INTO expense_items (id, expense_id, name, amount_cents, position) VALUES (?, ?, ?, ?, ?)",
        );
        const insertItemMember = this.db.prepare(
          "INSERT INTO expense_item_members (item_id, member_id, position) VALUES (?, ?, ?)",
        );
        input.split.items.forEach((item, position) => {
          const itemId = newId();
          insertItem.run(itemId, id, item.name, item.amountCents, position);
          item.memberIds.forEach((memberId, index) => insertItemMember.run(itemId, memberId, index));
        });
      }
    })();

    return this.getExpense(groupId, id);
  }

  deleteExpense(groupId: string, expenseId: string): void {
    const result = this.db.prepare("DELETE FROM expenses WHERE id = ? AND group_id = ?").run(expenseId, groupId);
    if (result.changes === 0) throw notFound("Deze uitgave bestaat niet (meer).");
  }
}
