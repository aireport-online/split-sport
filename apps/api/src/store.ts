import {
  GROUP_COLORS,
  computeShares,
  suggestTransfers,
  type Balance,
  type Expense,
  type ExpenseInput,
  type ExpenseItem,
  type ExpenseKind,
  type GroupColor,
  type GroupDetail,
  type GroupSummary,
  type Member,
  type Split,
  type SplitMode,
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
  currency: string;
  invite_code: string;
  created_at: string;
}

interface MemberRow {
  id: string;
  group_id: string;
  user_id: string | null;
  name: string;
}

interface ExpenseRow {
  id: string;
  group_id: string;
  kind: ExpenseKind;
  description: string;
  amount_cents: number;
  paid_by_member_id: string;
  date: string;
  split_mode: SplitMode;
  created_at: string;
  updated_at: string;
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
                (SELECT COUNT(*) FROM members x WHERE x.group_id = g.id) AS member_count,
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
        currency: row.currency,
        memberCount: row.member_count,
        myMemberId: row.my_member_id,
        myBalanceCents: row.my_balance,
        lastActivityAt: row.last_activity ?? row.created_at,
      }))
      .sort((a, b) => b.lastActivityAt.localeCompare(a.lastActivityAt));
  }

  createGroup(user: UserRow, input: { name: string; color?: GroupColor; currency: string }): GroupDetail {
    const id = newId();
    const now = new Date().toISOString();
    // Without a chosen colour, cycle through the palette so a user's groups look different.
    const count = this.db
      .prepare<[string], { n: number }>("SELECT COUNT(*) AS n FROM members WHERE user_id = ?")
      .get(user.id)!.n;
    const color = input.color ?? GROUP_COLORS[count % GROUP_COLORS.length]!;
    this.db.transaction(() => {
      this.db
        .prepare("INSERT INTO groups (id, name, color, currency, invite_code, created_at) VALUES (?, ?, ?, ?, ?, ?)")
        .run(id, input.name, color, input.currency, newInviteCode(), now);
      this.insertMember(id, user.name, user.id);
    })();
    return this.groupDetail(id, user.id);
  }

  updateGroup(groupId: string, patch: { name?: string; color?: GroupColor }): void {
    if (patch.name !== undefined) this.db.prepare("UPDATE groups SET name = ? WHERE id = ?").run(patch.name, groupId);
    if (patch.color !== undefined) this.db.prepare("UPDATE groups SET color = ? WHERE id = ?").run(patch.color, groupId);
  }

  /** The caller's member in a group; throws 404 when they are not in it. */
  requireMembership(groupId: string, userId: string): MemberRow {
    const member = this.db
      .prepare<[string, string], MemberRow>("SELECT * FROM members WHERE group_id = ? AND user_id = ?")
      .get(groupId, userId);
    if (!member) throw notFound("Deze groep bestaat niet of je bent er geen lid van.");
    return member;
  }

  groupDetail(groupId: string, userId: string): GroupDetail {
    const me = this.requireMembership(groupId, userId);
    const group = this.db.prepare<[string], GroupRow>("SELECT * FROM groups WHERE id = ?").get(groupId)!;
    const rows = this.db
      .prepare<[string], MemberRow & { iban: string | null; balance: number }>(
        `SELECT m.*, u.iban AS iban, ${BALANCE_SQL} AS balance
           FROM members m LEFT JOIN users u ON u.id = m.user_id
          WHERE m.group_id = ? ORDER BY m.created_at, m.rowid`,
      )
      .all(groupId);
    const members: Member[] = rows.map((r) => ({ id: r.id, name: r.name, userId: r.user_id, iban: r.iban }));
    const balances: Balance[] = rows.map((r) => ({ memberId: r.id, balanceCents: r.balance }));
    const total = this.db
      .prepare<[string], { total: number }>(
        "SELECT COALESCE(SUM(amount_cents), 0) AS total FROM expenses WHERE group_id = ? AND kind = 'expense'",
      )
      .get(groupId)!.total;
    return {
      id: group.id,
      name: group.name,
      color: group.color,
      currency: group.currency,
      inviteCode: group.invite_code,
      myMemberId: me.id,
      members,
      balances,
      transfers: suggestTransfers(balances),
      totalSpentCents: total,
    };
  }

  // ---- members ------------------------------------------------------------

  private insertMember(groupId: string, name: string, userId: string | null): string {
    const id = newId();
    this.db
      .prepare("INSERT INTO members (id, group_id, user_id, name, created_at) VALUES (?, ?, ?, ?, ?)")
      .run(id, groupId, userId, name, new Date().toISOString());
    return id;
  }

  addPlaceholderMember(groupId: string, name: string): string {
    return this.insertMember(groupId, name, null);
  }

  private requireMember(groupId: string, memberId: string): MemberRow {
    const member = this.db
      .prepare<[string, string], MemberRow>("SELECT * FROM members WHERE id = ? AND group_id = ?")
      .get(memberId, groupId);
    if (!member) throw notFound("Dit lid bestaat niet.");
    return member;
  }

  renameMember(groupId: string, memberId: string, name: string): void {
    const member = this.requireMember(groupId, memberId);
    if (member.user_id) {
      throw new HttpError(403, "forbidden", "Alleen leden zonder account kun je hernoemen.");
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
    if (member.user_id) {
      throw new HttpError(403, "forbidden", "Leden met een account kunnen alleen zelf de groep verlaten.");
    }
    if (this.memberHasEntries(memberId)) {
      throw new HttpError(409, "member_in_use", "Dit lid staat nog in uitgaven en kan niet worden verwijderd.");
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
      const remaining = this.db
        .prepare<[string], { n: number }>("SELECT COUNT(*) AS n FROM members WHERE group_id = ? AND user_id IS NOT NULL")
        .get(groupId)!.n;
      if (remaining === 0) this.deleteGroup(groupId);
    })();
  }

  private deleteGroup(groupId: string): void {
    // Expenses reference members without cascade, so remove them first.
    this.db.prepare("DELETE FROM expenses WHERE group_id = ?").run(groupId);
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

  invitePreview(code: string, userId: string) {
    const group = this.findGroupByInvite(code);
    const members = this.db
      .prepare<[string], MemberRow>("SELECT * FROM members WHERE group_id = ? ORDER BY created_at, rowid")
      .all(group.id);
    return {
      groupId: group.id,
      name: group.name,
      color: group.color,
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
          .prepare("UPDATE members SET user_id = ?, name = ? WHERE id = ? AND group_id = ? AND user_id IS NULL")
          .run(user.id, user.name, claimMemberId, group.id);
        if (result.changes === 0) {
          throw new HttpError(409, "member_taken", "Dit lid is al aan iemand anders gekoppeld.");
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
      kind: row.kind,
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
      if (!known.has(id)) throw new HttpError(400, "unknown_member", "Een van de leden hoort niet bij deze groep.");
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

  /** Creates (or, with `expenseId`, replaces) an expense or settlement. */
  saveExpense(groupId: string, kind: ExpenseKind, input: ExpenseInput, expenseId?: string): Expense {
    this.assertMembersInGroup(groupId, [input.paidById, ...Store.splitMemberIds(input.split)]);
    const shares = computeShares(input.amountCents, input.split);
    const weights = new Map(
      input.split.mode === "shares" ? input.split.weights.map((w) => [w.memberId, w.weight]) : [],
    );
    const now = new Date().toISOString();
    const id = expenseId ?? newId();

    this.db.transaction(() => {
      if (expenseId) {
        const result = this.db
          .prepare(
            `UPDATE expenses SET description = ?, amount_cents = ?, paid_by_member_id = ?, date = ?,
                    split_mode = ?, updated_at = ? WHERE id = ? AND group_id = ? AND kind = ?`,
          )
          .run(input.description, input.amountCents, input.paidById, input.date, input.split.mode, now, id, groupId, kind);
        if (result.changes === 0) throw notFound("Deze uitgave bestaat niet (meer).");
        this.db.prepare("DELETE FROM expense_shares WHERE expense_id = ?").run(id);
        this.db.prepare("DELETE FROM expense_items WHERE expense_id = ?").run(id);
      } else {
        this.db
          .prepare(
            `INSERT INTO expenses (id, group_id, kind, description, amount_cents, paid_by_member_id, date,
                                   split_mode, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          )
          .run(id, groupId, kind, input.description, input.amountCents, input.paidById, input.date, input.split.mode, now, now);
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
