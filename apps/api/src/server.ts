import { serve } from "@hono/node-server";
import { createApp } from "./app";
import { loadConfig } from "./config";
import { openDatabase } from "./db";
import { ClaudeReceiptScanner } from "./receipts";

const config = loadConfig();
const db = openDatabase(config.databasePath);

const app = createApp({
  db,
  receiptScanner: config.receiptScanEnabled ? new ClaudeReceiptScanner(config.receiptModel) : null,
  webDist: config.webDist,
  corsOrigin: config.corsOrigin,
  trustProxy: config.trustProxy,
});

const server = serve({ fetch: app.fetch, port: config.port }, (info) => {
  console.log(`Split API listening on http://localhost:${info.port}`);
  console.log(`Receipt scanning: ${config.receiptScanEnabled ? `on (${config.receiptModel})` : "off (set ANTHROPIC_API_KEY)"}`);
});

for (const signal of ["SIGINT", "SIGTERM"] as const) {
  process.on(signal, () => {
    server.close(() => {
      db.close();
      process.exit(0);
    });
  });
}
