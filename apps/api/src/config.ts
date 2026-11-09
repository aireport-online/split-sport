export interface Config {
  port: number;
  databasePath: string;
  /** Directory with the built web app; served when it exists. */
  webDist: string | null;
  /** Extra origin allowed to call the API from a browser (the web app itself needs none). */
  corsOrigin: string | null;
  /** Set when a reverse proxy in front of the server sets X-Forwarded-For. */
  trustProxy: boolean;
  receiptModel: string;
  receiptScanEnabled: boolean;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  return {
    port: Number(env.PORT ?? 8787),
    databasePath: env.DATABASE_PATH ?? "./data/split.db",
    webDist: env.WEB_DIST ?? null,
    corsOrigin: env.CORS_ORIGIN ?? null,
    trustProxy: env.TRUST_PROXY === "1",
    receiptModel: env.RECEIPT_MODEL ?? "claude-opus-5-5",
    receiptScanEnabled: Boolean(env.ANTHROPIC_API_KEY || env.ANTHROPIC_AUTH_TOKEN),
  };
}
