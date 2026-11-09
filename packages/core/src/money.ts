/** Formats integer cents for display, e.g. 123456 -> "€ 1.234,56" in nl-NL. */
export function formatCents(cents: number, currency = "EUR", locale = "nl-NL"): string {
  return new Intl.NumberFormat(locale, { style: "currency", currency }).format(cents / 100);
}

/**
 * Parses what a person typed as an amount ("12,50", "12.5", "1.234,56", "€ 12")
 * into integer cents. Returns null when it is not a valid non-negative amount.
 */
export function parseAmount(input: string): number | null {
  let text = input.replace(/[\s€$£]/g, "");
  if (text === "") return null;
  const lastComma = text.lastIndexOf(",");
  const lastDot = text.lastIndexOf(".");
  const decimalAt = Math.max(lastComma, lastDot);
  if (decimalAt !== -1) {
    const decimals = text.length - decimalAt - 1;
    const separator = text[decimalAt]!;
    const occurrences = text.split(separator).length - 1;
    // "1.234" or "1,234,567" is a thousands separator, "12,5" / "12.50" a decimal one.
    const isDecimal = decimals <= 2 && occurrences === 1;
    if (isDecimal) {
      text = text.slice(0, decimalAt).replace(/[.,]/g, "") + "." + text.slice(decimalAt + 1);
    } else if (decimals === 3) {
      text = text.replace(/[.,]/g, "");
    } else {
      return null;
    }
  }
  if (!/^\d+(\.\d{0,2})?$/.test(text)) return null;
  const [whole = "0", fraction = ""] = text.split(".");
  const cents = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  return Number.isSafeInteger(cents) ? cents : null;
}
