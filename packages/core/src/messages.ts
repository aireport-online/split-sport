import type { SplitErrorCode } from "./split";

/** Dutch, user-facing explanations of why a split cannot be computed. */
export const splitErrorMessages: Record<SplitErrorCode, string> = {
  invalid_amount: "Vul een geldig bedrag in.",
  no_participants: "Kies minstens één deelnemer.",
  duplicate_member: "Een deelnemer staat er dubbel in.",
  invalid_weight: "Vul geldige aandelen in.",
  exact_sum_mismatch: "De bedragen tellen niet op tot het totaal.",
  invalid_item: "Elke regel van de bon heeft een bedrag nodig.",
  item_unassigned: "Wijs elke regel van de bon aan iemand toe.",
};
