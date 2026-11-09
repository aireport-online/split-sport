import type { ContentfulStatusCode } from "hono/utils/http-status";
import type { SplitErrorCode } from "@split/core";

/** An error with a stable machine code and a Dutch message that clients can show as-is. */
export class HttpError extends Error {
  constructor(
    readonly status: ContentfulStatusCode,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "HttpError";
  }
}

export const notFound = (what = "Niet gevonden.") => new HttpError(404, "not_found", what);

export const splitErrorMessages: Record<SplitErrorCode, string> = {
  invalid_amount: "Vul een geldig bedrag in.",
  no_participants: "Kies minstens één deelnemer.",
  duplicate_member: "Een deelnemer staat er dubbel in.",
  invalid_weight: "Vul geldige aandelen in.",
  exact_sum_mismatch: "De bedragen tellen niet op tot het totaal.",
  invalid_item: "Elke regel van de bon heeft een bedrag nodig.",
  item_unassigned: "Wijs elke regel van de bon aan iemand toe.",
};
