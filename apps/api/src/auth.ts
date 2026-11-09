import { createHash, randomBytes, randomUUID, scrypt, timingSafeEqual } from "node:crypto";
import type { MiddlewareHandler } from "hono";
import type { Db } from "./db";
import { HttpError } from "./errors";

export interface UserRow {
  id: string;
  email: string;
  name: string;
  password_hash: string;
  iban: string | null;
  created_at: string;
}

export type AppEnv = { Variables: { user: UserRow; tokenHash: string } };

const KEY_LENGTH = 64;
const SCRYPT_COST = 2 ** 15;

function derive(password: string, salt: Buffer, cost: number): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    scrypt(password, salt, KEY_LENGTH, { N: cost, maxmem: 128 * cost * 8 * 2 }, (error, key) =>
      error ? reject(error) : resolve(key),
    );
  });
}

export async function hashPassword(password: string): Promise<string> {
  const salt = randomBytes(16);
  const key = await derive(password, salt, SCRYPT_COST);
  return `scrypt$${SCRYPT_COST}$${salt.toString("base64")}$${key.toString("base64")}`;
}

export async function verifyPassword(password: string, stored: string): Promise<boolean> {
  const [scheme, cost, salt, key] = stored.split("$");
  if (scheme !== "scrypt" || !cost || !salt || !key) return false;
  const expected = Buffer.from(key, "base64");
  const actual = await derive(password, Buffer.from(salt, "base64"), Number(cost));
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

const hashToken = (token: string) => createHash("sha256").update(token).digest("hex");

/** Creates a session and returns the bearer token. Only its hash is stored. */
export function createSession(db: Db, userId: string): string {
  const token = randomBytes(32).toString("base64url");
  const now = new Date().toISOString();
  db.prepare("INSERT INTO sessions (token_hash, user_id, created_at, last_used_at) VALUES (?, ?, ?, ?)").run(
    hashToken(token),
    userId,
    now,
    now,
  );
  return token;
}

export function requireAuth(db: Db): MiddlewareHandler<AppEnv> {
  const findUser = db.prepare<[string], UserRow>(
    "SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ?",
  );
  const touch = db.prepare("UPDATE sessions SET last_used_at = ? WHERE token_hash = ? AND last_used_at < ?");
  return async (c, next) => {
    const header = c.req.header("Authorization") ?? "";
    const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
    const tokenHash = token ? hashToken(token) : "";
    const user = tokenHash ? findUser.get(tokenHash) : undefined;
    if (!user) throw new HttpError(401, "unauthorized", "Log opnieuw in.");
    // Refresh at most once per hour to keep reads cheap.
    const now = new Date();
    touch.run(now.toISOString(), tokenHash, new Date(now.getTime() - 3_600_000).toISOString());
    c.set("user", user);
    c.set("tokenHash", tokenHash);
    await next();
  };
}

export const newId = () => randomUUID();

const INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

/** An 8-character code without look-alike characters (no 0/O, 1/I). */
export function newInviteCode(): string {
  const bytes = randomBytes(8);
  let code = "";
  for (const byte of bytes) code += INVITE_ALPHABET[byte % INVITE_ALPHABET.length];
  return code;
}
