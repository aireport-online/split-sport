import { describe, expect, it } from "vitest";
import { formatCents, parseAmount } from "../src";

describe("parseAmount", () => {
  it.each([
    ["12,50", 1250],
    ["12.5", 1250],
    ["12", 1200],
    ["€ 12,-".replace(",-", ""), 1200],
    ["1.234,56", 123456],
    ["1,234.56", 123456],
    ["1.234", 123400],
    ["0,05", 5],
    [" 7,5 ", 750],
    ["12,", 1200],
  ])("parses %s", (input, cents) => {
    expect(parseAmount(input)).toBe(cents);
  });

  it.each(["", "abc", "1,2,3", "-5", "12,345,6", "1.2345"])("rejects %s", (input) => {
    expect(parseAmount(input)).toBeNull();
  });
});

describe("formatCents", () => {
  it("formats euros the Dutch way", () => {
    expect(formatCents(123456).replace(/ /g, " ")).toBe("€ 1.234,56");
    expect(formatCents(-50).replace(/ /g, " ")).toBe("€ -0,50");
  });
});
