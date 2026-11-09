import { GROUP_COLORS, formatCents, type GroupColor, type Member } from "@split/core";

/** "€ 1.234,56" with a normal space, so it lines up in tabular columns. */
export const money = (cents: number, currency = "EUR"): string =>
  formatCents(Math.abs(cents), currency).replace(/ /g, " ");

const plainFormat = new Intl.NumberFormat("nl-NL", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** An amount as a till prints it, without currency sign: "1.234,56". */
export const plain = (cents: number): string => plainFormat.format(Math.abs(cents) / 100);

/** A balance on a receipt line: "+ 24,95", "− 21,60" or "0,00". */
export function signedPlain(cents: number): string {
  if (cents === 0) return plain(0);
  return `${cents > 0 ? "+" : "−"} ${plain(cents)}`;
}

/** A balance with its sign: "+ € 12,50", "− € 8,00" or "€ 0,00". */
export function signedMoney(cents: number, currency = "EUR"): string {
  if (cents === 0) return money(0, currency);
  return `${cents > 0 ? "+" : "−"} ${money(cents, currency)}`;
}

/** What a balance means in words: "krijgt € 12,50", "betaalt € 8,00" or "quitte". */
export function balanceWords(cents: number, currency = "EUR"): string {
  if (cents === 0) return "quitte";
  return `${cents > 0 ? "krijgt" : "betaalt"} ${money(cents, currency)}`;
}

/** The signed-in member's balance as a sentence. */
export function myBalanceSentence(cents: number, currency = "EUR"): string {
  if (cents === 0) return "Je staat quitte";
  return cents > 0 ? `Je krijgt ${money(cents, currency)}` : `Je bent ${money(cents, currency)} verschuldigd`;
}

/** A member's highlighter follows their position in the group, as on the other clients. */
export const memberColor = (members: Member[], memberId: string): GroupColor =>
  GROUP_COLORS[Math.max(0, members.findIndex((m) => m.id === memberId)) % GROUP_COLORS.length]!;

export const todayIso = (): string => {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
};

const dayFormat = new Intl.DateTimeFormat("nl-NL", { day: "numeric", month: "short" });
const dayYearFormat = new Intl.DateTimeFormat("nl-NL", { day: "numeric", month: "short", year: "numeric" });
const monthFormat = new Intl.DateTimeFormat("nl-NL", { month: "long", year: "numeric" });

const parseDate = (iso: string) => new Date(`${iso}T12:00:00`);

/** "2 nov", with the year when it is not the current one. */
export function shortDate(iso: string): string {
  const date = parseDate(iso);
  const format = date.getFullYear() === new Date().getFullYear() ? dayFormat : dayYearFormat;
  return format.format(date).replace(".", "");
}

/** "November 2026", used as a heading above the expenses of a month. */
export function monthHeading(iso: string): string {
  const text = monthFormat.format(parseDate(iso));
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** "NL91 ABNA 0417 1643 00" */
export const formatIban = (iban: string): string => iban.replace(/(.{4})/g, "$1 ").trim();

export const memberName = (members: Member[], memberId: string): string =>
  members.find((m) => m.id === memberId)?.name ?? "Onbekend";
