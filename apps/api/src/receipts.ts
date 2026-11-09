import Anthropic from "@anthropic-ai/sdk";
import { betaZodOutputFormat } from "@anthropic-ai/sdk/helpers/beta/zod";
import { MAX_AMOUNT_CENTS, type ReceiptScan } from "@split/core";
import { z } from "zod";
import { HttpError } from "./errors";

export type ReceiptMediaType = "image/jpeg" | "image/png" | "image/webp" | "image/gif";

export interface ReceiptScanner {
  scan(imageBase64: string, mediaType: ReceiptMediaType): Promise<ReceiptScan>;
}

const receiptSchema = z.object({
  isReceipt: z.boolean().describe("False when the image does not show a receipt, bill or invoice."),
  merchant: z.string().nullable().describe("Name of the shop or restaurant, or null when unreadable."),
  date: z.string().nullable().describe("Date printed on the receipt as YYYY-MM-DD, or null when absent."),
  totalCents: z
    .number()
    .int()
    .nullable()
    .describe("The final amount that was paid, in cents, including tip and service charge. Null when unreadable."),
  items: z
    .array(
      z.object({
        name: z.string().describe("The line as a person would recognise it, e.g. '2× Cappuccino'."),
        amountCents: z.number().int().describe("The total price of the line in cents, after line discounts."),
      }),
    )
    .describe("The purchased lines in the order they are printed."),
});

const SYSTEM_PROMPT = `You read photos of receipts for an app that splits a bill among friends. \
People will tap each line to say who had it, so every purchased product or dish becomes one item with the price that was charged for that line.

How to read the receipt:
- Amounts are integer cents: "€ 12,50" is 1250. Dutch receipts use a comma as the decimal separator.
- A line with a quantity ("2 x 4,50  9,00") is one item with the line total (900). Keep the quantity in the name: "2× Bier".
- A discount printed directly under a product belongs to that product: subtract it from that item's amount.
- Deposit (statiegeld) and service or delivery charges that are printed as their own line are items too.
- Leave out everything that is not a purchase: subtotals, the total, VAT/BTW breakdowns, payment and change lines, loyalty points, discounts that apply to the whole receipt. The app spreads any difference between the items and the total over everyone in proportion.
- Leave out lines with a zero or negative amount.
- totalCents is what was actually paid. When a tip was added by hand, use the amount including the tip.
- Write names as printed, expanding obvious abbreviations when you are sure. Do not translate.
- When a value is unreadable, use null instead of guessing. When the photo is not a receipt, set isReceipt to false and return no items.`;

/** Reads receipts with Claude's vision and structured outputs. */
export class ClaudeReceiptScanner implements ReceiptScanner {
  private readonly client = new Anthropic();

  constructor(private readonly model: string) {}

  async scan(imageBase64: string, mediaType: ReceiptMediaType): Promise<ReceiptScan> {
    let response;
    try {
      response = await this.client.beta.messages.parse({
        model: this.model,
        max_tokens: 16000,
        // Reading a receipt is a simple extraction; low effort keeps it fast and cheap.
        output_config: { effort: "low", format: betaZodOutputFormat(receiptSchema) },
        // If the model's safety classifiers decline a photo, let the API retry on its default fallback model.
        betas: ["server-side-fallback-2026-07-01"],
        fallbacks: "default",
        system: SYSTEM_PROMPT,
        messages: [
          {
            role: "user",
            content: [
              { type: "image", source: { type: "base64", media_type: mediaType, data: imageBase64 } },
              { type: "text", text: "Read this receipt." },
            ],
          },
        ],
      });
    } catch (error) {
      if (error instanceof Anthropic.BadRequestError) {
        throw new HttpError(422, "scan_failed", "Deze foto kon niet worden gelezen. Probeer een andere foto.");
      }
      if (error instanceof Anthropic.RateLimitError) {
        throw new HttpError(503, "scan_busy", "Bon scannen is even druk. Probeer het zo opnieuw.");
      }
      if (error instanceof Anthropic.APIError) {
        console.error("Receipt scan failed:", error.status, error.message);
        throw new HttpError(502, "scan_failed", "Bon scannen is niet gelukt. Probeer het opnieuw of vul de bon zelf in.");
      }
      throw error;
    }

    const parsed = response.parsed_output;
    if (response.stop_reason === "refusal" || response.stop_reason === "max_tokens" || !parsed) {
      throw new HttpError(422, "scan_failed", "Deze foto kon niet worden gelezen. Probeer een andere foto.");
    }
    return normalizeReceipt(parsed);
  }
}

/** Drops unusable lines and values so clients can prefill an expense without further checks. */
export function normalizeReceipt(parsed: z.infer<typeof receiptSchema>): ReceiptScan {
  const items = parsed.items
    .map((item) => ({ name: item.name.trim().slice(0, 120), amountCents: item.amountCents }))
    .filter((item) => item.name !== "" && item.amountCents > 0 && item.amountCents <= MAX_AMOUNT_CENTS);
  if (!parsed.isReceipt || items.length === 0) {
    throw new HttpError(422, "no_receipt", "We zien geen bon op deze foto. Probeer het opnieuw of vul de bon zelf in.");
  }
  const total = parsed.totalCents;
  const date = parsed.date && /^\d{4}-\d{2}-\d{2}$/.test(parsed.date) ? parsed.date : null;
  return {
    merchant: parsed.merchant?.trim().slice(0, 120) || null,
    date,
    totalCents: total !== null && total > 0 && total <= MAX_AMOUNT_CENTS ? total : null,
    items,
  };
}
