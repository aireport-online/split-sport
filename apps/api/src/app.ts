import { serveStatic } from "@hono/node-server/serve-static";
import { SplitError, splitErrorMessages, type AuthResponse, type InvitePreview, type ServerConfig } from "@split/core";
import { Hono, type Context } from "hono";
import { bodyLimit } from "hono/body-limit";
import { cors } from "hono/cors";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { z } from "zod";
import { createSession, hashPassword, newId, requireAuth, verifyPassword, type AppEnv, type UserRow } from "./auth";
import type { Db } from "./db";
import { HttpError } from "./errors";
import { clientIp, rateLimit } from "./rate-limit";
import type { ReceiptScanner } from "./receipts";
import {
  createGroupSchema,
  expenseSchema,
  joinSchema,
  loginSchema,
  memberNameSchema,
  registerSchema,
  scanSchema,
  settlementSchema,
  updateGroupSchema,
  updateMeSchema,
} from "./schemas";
import { Store, toUser } from "./store";

export interface AppOptions {
  db: Db;
  /** Null when no Anthropic credentials are configured. */
  receiptScanner: ReceiptScanner | null;
  webDist?: string | null;
  corsOrigin?: string | null;
  trustProxy?: boolean;
  /** Tests switch the limiter off. */
  rateLimits?: boolean;
}

const KB = 1024;

/** Parses a JSON body against a schema; anything invalid becomes a 400 with a Dutch message. */
async function body<T extends z.ZodType>(c: Context, schema: T): Promise<z.infer<T>> {
  let json: unknown;
  try {
    json = await c.req.json();
  } catch {
    throw new HttpError(400, "invalid_json", "Het verzoek is ongeldig.");
  }
  const result = schema.safeParse(json);
  if (!result.success) {
    const field = result.error.issues[0]?.path.join(".") ?? "";
    throw new HttpError(400, "validation_error", validationMessage(field));
  }
  return result.data;
}

function validationMessage(field: string): string {
  if (field === "email") return "Vul een geldig e-mailadres in.";
  if (field === "password") return "Kies een wachtwoord van minstens 8 tekens.";
  if (field === "name") return "Vul een naam in (maximaal 60 tekens).";
  if (field === "iban") return "Dit IBAN klopt niet.";
  if (field === "description") return "Geef de uitgave een omschrijving.";
  if (field === "date") return "Kies een geldige datum.";
  if (field.endsWith("amountCents")) return "Vul een geldig bedrag in.";
  return "Controleer wat je hebt ingevuld.";
}

export function createApp(options: AppOptions): Hono<AppEnv> {
  const { db } = options;
  const store = new Store(db);
  const app = new Hono<AppEnv>();
  const api = new Hono<AppEnv>();
  const auth = requireAuth(db);
  const limits = options.rateLimits ?? true;
  const limit = (max: number, windowMs: number, key: (c: Context) => string) =>
    limits ? rateLimit({ limit: max, windowMs, key }) : async (_c: Context, next: () => Promise<void>) => next();
  const ip = (c: Context) => clientIp(c, options.trustProxy ?? false);

  if (options.corsOrigin) api.use("*", cors({ origin: options.corsOrigin }));
  api.use("*", async (c, next) => {
    const max = c.req.path === "/api/receipts/scan" ? 12 * KB * KB : 256 * KB;
    return bodyLimit({
      maxSize: max,
      onError: () => {
        throw new HttpError(413, "too_large", "Dit bestand is te groot.");
      },
    })(c, next);
  });

  // ---- public -------------------------------------------------------------

  api.get("/health", (c) => c.json({ ok: true }));

  api.get("/config", (c) => c.json<ServerConfig>({ receiptScanEnabled: options.receiptScanner !== null }));

  const authLimit = limit(20, 10 * 60_000, (c) => `auth:${ip(c)}`);

  api.post("/auth/register", authLimit, async (c) => {
    const input = await body(c, registerSchema);
    const exists = db.prepare("SELECT 1 FROM users WHERE email = ?").get(input.email);
    if (exists) throw new HttpError(409, "email_taken", "Er bestaat al een account met dit e-mailadres.");
    const user: UserRow = {
      id: newId(),
      email: input.email,
      name: input.name,
      password_hash: await hashPassword(input.password),
      iban: null,
      created_at: new Date().toISOString(),
    };
    db.prepare(
      "INSERT INTO users (id, email, name, password_hash, iban, created_at) VALUES (@id, @email, @name, @password_hash, @iban, @created_at)",
    ).run(user);
    return c.json<AuthResponse>({ token: createSession(db, user.id), user: toUser(user) }, 201);
  });

  api.post("/auth/login", authLimit, async (c) => {
    const input = await body(c, loginSchema);
    const user = db.prepare<[string], UserRow>("SELECT * FROM users WHERE email = ?").get(input.email);
    // Hash even when the account does not exist, so timing does not reveal which emails are registered.
    const ok = user
      ? await verifyPassword(input.password, user.password_hash)
      : (await hashPassword(input.password), false);
    if (!user || !ok) throw new HttpError(401, "invalid_credentials", "E-mailadres of wachtwoord klopt niet.");
    return c.json<AuthResponse>({ token: createSession(db, user.id), user: toUser(user) });
  });

  // ---- account ------------------------------------------------------------

  api.post("/auth/logout", auth, (c) => {
    db.prepare("DELETE FROM sessions WHERE token_hash = ?").run(c.get("tokenHash"));
    return c.body(null, 204);
  });

  api.get("/me", auth, (c) => c.json(toUser(c.get("user"))));

  api.patch("/me", auth, async (c) => {
    const user = c.get("user");
    const input = await body(c, updateMeSchema);
    db.transaction(() => {
      if (input.name !== undefined) {
        db.prepare("UPDATE users SET name = ? WHERE id = ?").run(input.name, user.id);
        store.renameUserEverywhere(user.id, input.name);
      }
      if (input.iban !== undefined) db.prepare("UPDATE users SET iban = ? WHERE id = ?").run(input.iban, user.id);
    })();
    return c.json(toUser(db.prepare<[string], UserRow>("SELECT * FROM users WHERE id = ?").get(user.id)!));
  });

  api.delete("/me", auth, (c) => {
    store.deleteAccount(c.get("user").id);
    return c.body(null, 204);
  });

  // ---- groups -------------------------------------------------------------

  api.get("/groups", auth, (c) => c.json({ groups: store.listGroups(c.get("user").id) }));

  api.post("/groups", auth, async (c) => {
    const input = await body(c, createGroupSchema);
    return c.json(store.createGroup(c.get("user"), input), 201);
  });

  api.get("/groups/:groupId", auth, (c) => c.json(store.groupDetail(c.req.param("groupId"), c.get("user").id)));

  api.patch("/groups/:groupId", auth, async (c) => {
    const groupId = c.req.param("groupId");
    store.requireMembership(groupId, c.get("user").id);
    store.updateGroup(groupId, await body(c, updateGroupSchema));
    return c.json(store.groupDetail(groupId, c.get("user").id));
  });

  api.post("/groups/:groupId/leave", auth, (c) => {
    store.leaveGroup(c.req.param("groupId"), c.get("user").id);
    return c.body(null, 204);
  });

  // ---- members ------------------------------------------------------------

  api.post("/groups/:groupId/members", auth, async (c) => {
    const groupId = c.req.param("groupId");
    store.requireMembership(groupId, c.get("user").id);
    const { name } = await body(c, memberNameSchema);
    store.addPlaceholderMember(groupId, name);
    return c.json(store.groupDetail(groupId, c.get("user").id), 201);
  });

  api.patch("/groups/:groupId/members/:memberId", auth, async (c) => {
    const groupId = c.req.param("groupId");
    store.requireMembership(groupId, c.get("user").id);
    const { name } = await body(c, memberNameSchema);
    store.renameMember(groupId, c.req.param("memberId"), name);
    return c.json(store.groupDetail(groupId, c.get("user").id));
  });

  api.delete("/groups/:groupId/members/:memberId", auth, (c) => {
    const groupId = c.req.param("groupId");
    store.requireMembership(groupId, c.get("user").id);
    store.removePlaceholderMember(groupId, c.req.param("memberId"));
    return c.json(store.groupDetail(groupId, c.get("user").id));
  });

  // ---- invites ------------------------------------------------------------

  api.get("/invites/:code", auth, (c) =>
    c.json<InvitePreview>(store.invitePreview(c.req.param("code"), c.get("user").id)),
  );

  api.post("/invites/:code/join", auth, async (c) => {
    const { memberId } = await body(c, joinSchema);
    return c.json(store.joinGroup(c.req.param("code"), c.get("user"), memberId));
  });

  // ---- expenses and settlements ------------------------------------------

  api.get("/groups/:groupId/expenses", auth, (c) => {
    const groupId = c.req.param("groupId");
    store.requireMembership(groupId, c.get("user").id);
    return c.json({ expenses: store.listExpenses(groupId) });
  });

  api.post("/groups/:groupId/expenses", auth, async (c) => {
    const groupId = c.req.param("groupId");
    store.requireMembership(groupId, c.get("user").id);
    return c.json(store.saveExpense(groupId, "expense", await body(c, expenseSchema)), 201);
  });

  api.get("/groups/:groupId/expenses/:expenseId", auth, (c) => {
    const groupId = c.req.param("groupId");
    store.requireMembership(groupId, c.get("user").id);
    return c.json(store.getExpense(groupId, c.req.param("expenseId")));
  });

  api.put("/groups/:groupId/expenses/:expenseId", auth, async (c) => {
    const groupId = c.req.param("groupId");
    store.requireMembership(groupId, c.get("user").id);
    const input = await body(c, expenseSchema);
    return c.json(store.saveExpense(groupId, "expense", input, c.req.param("expenseId")));
  });

  api.delete("/groups/:groupId/expenses/:expenseId", auth, (c) => {
    const groupId = c.req.param("groupId");
    store.requireMembership(groupId, c.get("user").id);
    store.deleteExpense(groupId, c.req.param("expenseId"));
    return c.body(null, 204);
  });

  api.post("/groups/:groupId/settlements", auth, async (c) => {
    const groupId = c.req.param("groupId");
    store.requireMembership(groupId, c.get("user").id);
    const input = await body(c, settlementSchema);
    if (input.fromMemberId === input.toMemberId) {
      throw new HttpError(400, "validation_error", "Kies twee verschillende leden.");
    }
    const expense = store.saveExpense(groupId, "settlement", {
      description: "Betaling",
      amountCents: input.amountCents,
      date: input.date ?? new Date().toISOString().slice(0, 10),
      paidById: input.fromMemberId,
      split: { mode: "exact", amounts: [{ memberId: input.toMemberId, amountCents: input.amountCents }] },
    });
    return c.json(expense, 201);
  });

  // ---- receipts -----------------------------------------------------------

  api.post(
    "/receipts/scan",
    auth,
    limit(30, 60 * 60_000, (c) => `scan:${(c as Context<AppEnv>).get("user").id}`),
    async (c) => {
      if (!options.receiptScanner) {
        throw new HttpError(503, "scan_unavailable", "Bon scannen staat op deze server niet aan.");
      }
      const input = await body(c, scanSchema);
      return c.json(await options.receiptScanner.scan(input.imageBase64, input.mediaType));
    },
  );

  // Unknown API paths must answer JSON, never fall through to the web app below.
  api.all("*", () => {
    throw new HttpError(404, "not_found", "Niet gevonden.");
  });

  api.onError((error, c) => {
    if (error instanceof HttpError) {
      return c.json({ error: { code: error.code, message: error.message } }, error.status);
    }
    if (error instanceof SplitError) {
      return c.json({ error: { code: error.code, message: splitErrorMessages[error.code] } }, 400);
    }
    console.error(error);
    return c.json({ error: { code: "internal_error", message: "Er ging iets mis. Probeer het opnieuw." } }, 500);
  });

  app.route("/api", api);

  // ---- web app ------------------------------------------------------------

  const webDist = options.webDist;
  if (webDist && existsSync(join(webDist, "index.html"))) {
    const index = readFileSync(join(webDist, "index.html"), "utf8");
    app.use(
      "/assets/*",
      serveStatic({
        root: webDist,
        onFound: (_path, c) => c.header("Cache-Control", "public, max-age=31536000, immutable"),
      }),
    );
    app.use("*", serveStatic({ root: webDist }));
    // Client-side routes (/groups/…, /join/…) all load the single-page app.
    app.get("*", (c) => c.html(index));
  }

  return app;
}
