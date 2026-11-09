import { describe, expect, it } from "vitest";
import vectors from "../../../spec/split-vectors.json";
import { allocate, computeShares, SplitError, type Split } from "../src";

describe("computeShares (shared vectors)", () => {
  for (const testCase of vectors.cases) {
    it(testCase.name, () => {
      const run = () => computeShares(testCase.amountCents, testCase.split as Split);
      if ("error" in testCase) {
        expect(run).toThrowError(SplitError);
        try {
          run();
        } catch (error) {
          expect((error as SplitError).code).toBe(testCase.error);
        }
      } else {
        const shares = run();
        expect(shares).toEqual(testCase.expected);
        expect(shares.reduce((sum, s) => sum + s.amountCents, 0)).toBe(testCase.amountCents);
      }
    });
  }
});

describe("allocate", () => {
  it("always sums to the total", () => {
    for (let total = 0; total < 500; total += 7) {
      for (const weights of [[1, 1, 1], [3, 2, 1, 1], [5], [1, 0, 2], [7, 11, 13, 17]]) {
        expect(allocate(total, weights).reduce((a, b) => a + b, 0)).toBe(total);
      }
    }
  });

  it("handles a negative total", () => {
    expect(allocate(-100, [1, 1, 1])).toEqual([-34, -33, -33]);
    expect(allocate(-1, [1, 1])).toEqual([-1, 0]);
  });
});
