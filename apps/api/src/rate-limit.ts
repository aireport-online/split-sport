import { getConnInfo } from "@hono/node-server/conninfo";
import type { Context, MiddlewareHandler } from "hono";
import { HttpError } from "./errors";

/**
 * A small in-memory fixed-window limiter. Enough to slow down password guessing
 * and to cap receipt scans on a single-instance deployment.
 */
export function rateLimit(options: {
  limit: number;
  windowMs: number;
  key: (c: Context) => string;
}): MiddlewareHandler {
  const hits = new Map<string, { count: number; resetAt: number }>();
  return async (c, next) => {
    const now = Date.now();
    if (hits.size > 10_000) {
      for (const [key, entry] of hits) if (entry.resetAt <= now) hits.delete(key);
    }
    const key = options.key(c);
    const entry = hits.get(key);
    if (!entry || entry.resetAt <= now) {
      hits.set(key, { count: 1, resetAt: now + options.windowMs });
    } else if (++entry.count > options.limit) {
      c.header("Retry-After", String(Math.ceil((entry.resetAt - now) / 1000)));
      throw new HttpError(429, "rate_limited", "Te veel pogingen. Probeer het zo opnieuw.");
    }
    await next();
  };
}

/**
 * The caller's address. `X-Forwarded-For` is only believed when the server runs
 * behind a reverse proxy (TRUST_PROXY=1); otherwise anyone could spoof it.
 */
export function clientIp(c: Context, trustProxy: boolean): string {
  const forwarded = trustProxy ? c.req.header("x-forwarded-for")?.split(",")[0]?.trim() : undefined;
  if (forwarded) return forwarded;
  try {
    return getConnInfo(c).remote.address ?? "unknown";
  } catch {
    return "unknown";
  }
}
