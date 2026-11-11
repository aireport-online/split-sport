import Database from "better-sqlite3";
import { mkdirSync } from "node:fs";
import { dirname } from "node:path";

export type Db = Database.Database;

/**
 * Schema migrations, applied in order. The index in this array + 1 is stored in
 * SQLite's `user_version`, so only append; never edit a shipped migration.
 */
const migrations: string[] = [
  `
  CREATE TABLE users (
    id            TEXT PRIMARY KEY,
    email         TEXT NOT NULL UNIQUE COLLATE NOCASE,
    name          TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    iban          TEXT,
    created_at    TEXT NOT NULL
  );

  CREATE TABLE sessions (
    token_hash   TEXT PRIMARY KEY,
    user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at   TEXT NOT NULL,
    last_used_at TEXT NOT NULL
  );
  CREATE INDEX sessions_user ON sessions(user_id);

  CREATE TABLE groups (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    color       TEXT NOT NULL,
    currency    TEXT NOT NULL,
    invite_code TEXT NOT NULL UNIQUE,
    created_at  TEXT NOT NULL
  );

  -- A member belongs to one group. user_id is NULL for people added by name
  -- who have not (yet) joined with an account.
  CREATE TABLE members (
    id         TEXT PRIMARY KEY,
    group_id   TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id    TEXT REFERENCES users(id) ON DELETE SET NULL,
    name       TEXT NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE (group_id, user_id)
  );
  CREATE INDEX members_user ON members(user_id);

  -- Expenses and settlements share one table: a settlement is an entry paid
  -- by the debtor with a single share for the creditor.
  CREATE TABLE expenses (
    id                TEXT PRIMARY KEY,
    group_id          TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    kind              TEXT NOT NULL CHECK (kind IN ('expense', 'settlement')),
    description       TEXT NOT NULL,
    amount_cents      INTEGER NOT NULL CHECK (amount_cents > 0),
    paid_by_member_id TEXT NOT NULL REFERENCES members(id),
    date              TEXT NOT NULL,
    split_mode        TEXT NOT NULL CHECK (split_mode IN ('equal', 'exact', 'shares', 'items')),
    created_at        TEXT NOT NULL,
    updated_at        TEXT NOT NULL
  );
  CREATE INDEX expenses_group ON expenses(group_id, date DESC, created_at DESC);
  CREATE INDEX expenses_payer ON expenses(paid_by_member_id);

  CREATE TABLE expense_shares (
    expense_id   TEXT NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    member_id    TEXT NOT NULL REFERENCES members(id),
    amount_cents INTEGER NOT NULL,
    weight       INTEGER,
    position     INTEGER NOT NULL,
    PRIMARY KEY (expense_id, member_id)
  );
  CREATE INDEX expense_shares_member ON expense_shares(member_id);

  CREATE TABLE expense_items (
    id           TEXT PRIMARY KEY,
    expense_id   TEXT NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    amount_cents INTEGER NOT NULL,
    position     INTEGER NOT NULL
  );
  CREATE INDEX expense_items_expense ON expense_items(expense_id);

  CREATE TABLE expense_item_members (
    item_id   TEXT NOT NULL REFERENCES expense_items(id) ON DELETE CASCADE,
    member_id TEXT NOT NULL REFERENCES members(id),
    position  INTEGER NOT NULL,
    PRIMARY KEY (item_id, member_id)
  );
  CREATE INDEX expense_item_members_member ON expense_item_members(member_id);
  `,

  // Sports teams: every group is a team with a sport, a pot (teamkas), a
  // treasurer and a fine list. Fines and contributions are expenses with an
  // entry_type; the pot is a member of kind 'pot'.
  `
  ALTER TABLE groups ADD COLUMN sport TEXT NOT NULL DEFAULT 'overig';
  ALTER TABLE groups ADD COLUMN club TEXT;
  ALTER TABLE groups ADD COLUMN season TEXT;
  ALTER TABLE groups ADD COLUMN treasurer_member_id TEXT REFERENCES members(id) ON DELETE SET NULL;

  ALTER TABLE members ADD COLUMN kind TEXT NOT NULL DEFAULT 'player' CHECK (kind IN ('player', 'pot'));
  ALTER TABLE expenses ADD COLUMN entry_type TEXT CHECK (entry_type IN ('fine', 'contribution'));

  CREATE TABLE fines (
    id           TEXT PRIMARY KEY,
    group_id     TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    position     INTEGER NOT NULL
  );
  CREATE INDEX fines_group ON fines(group_id, position);

  -- Groups that already existed get their pot, and their first member with an account as treasurer.
  INSERT INTO members (id, group_id, user_id, name, kind, created_at)
    SELECT lower(hex(randomblob(16))), id, NULL, 'Teamkas', 'pot', created_at FROM groups;
  UPDATE groups SET treasurer_member_id = (
    SELECT m.id FROM members m
     WHERE m.group_id = groups.id AND m.user_id IS NOT NULL
     ORDER BY m.created_at, m.rowid LIMIT 1
  );
  `,
];

export function openDatabase(path: string): Db {
  if (path !== ":memory:") mkdirSync(dirname(path), { recursive: true });
  const db = new Database(path);
  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");
  db.pragma("busy_timeout = 5000");
  migrate(db);
  return db;
}

function migrate(db: Db): void {
  const current = db.pragma("user_version", { simple: true }) as number;
  for (let version = current; version < migrations.length; version++) {
    db.transaction(() => {
      db.exec(migrations[version]!);
      db.pragma(`user_version = ${version + 1}`);
    })();
  }
}
