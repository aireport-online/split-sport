import { parseAmount, type Expense, type GroupDetail } from "@split/core";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Dialog, ErrorBanner, Field } from "../components/ui";
import { api, errorMessage } from "../lib/api";
import { todayIso } from "../lib/format";
import { useInvalidateGroups } from "../lib/queries";
import { amountText } from "./draft";

export interface SettlementDraft {
  fromMemberId: string;
  toMemberId: string;
  amountCents: number;
  date?: string;
}

/**
 * Records that one member paid another back. An existing payment can only be
 * removed (and recorded again), which keeps the history honest.
 */
export function SettlementDialog({
  group,
  draft,
  expense,
  onClose,
}: {
  group: GroupDetail;
  draft: SettlementDraft;
  expense: Expense | null;
  onClose: () => void;
}) {
  const invalidate = useInvalidateGroups();
  const [from, setFrom] = useState(draft.fromMemberId);
  const [to, setTo] = useState(draft.toMemberId);
  const [amount, setAmount] = useState(amountText(draft.amountCents));
  const [date, setDate] = useState(draft.date ?? todayIso());
  const existing = expense !== null;

  const save = useMutation({
    mutationFn: async () => {
      const amountCents = parseAmount(amount);
      if (amountCents === null || amountCents <= 0) throw new Error("Vul een geldig bedrag in.");
      if (from === to) throw new Error("Kies twee verschillende leden.");
      await api.createSettlement(group.id, { fromMemberId: from, toMemberId: to, amountCents, date });
    },
    onSuccess: async () => {
      await invalidate();
      onClose();
    },
  });

  const remove = useMutation({
    mutationFn: () => api.deleteExpense(group.id, expense!.id),
    onSuccess: async () => {
      await invalidate();
      onClose();
    },
  });

  const error = save.error ?? remove.error;

  return (
    <Dialog
      title="Betaling"
      narrow
      onClose={onClose}
      onSubmit={existing ? undefined : () => save.mutate()}
      footer={
        existing ? (
          <>
            <Button variant="danger" onClick={() => remove.mutate()} disabled={remove.isPending}>
              Betaling verwijderen
            </Button>
            <span className="spacer" />
            <Button onClick={onClose}>Sluiten</Button>
          </>
        ) : (
          <>
            <span className="spacer" />
            <Button onClick={onClose}>Annuleren</Button>
            <Button variant="primary" type="submit" disabled={save.isPending}>
              Betaling opslaan
            </Button>
          </>
        )
      }
    >
      <div className="field-row">
        <Field label="Van">
          <select className="select" value={from} onChange={(event) => setFrom(event.target.value)} disabled={existing}>
            {group.members.map((member) => (
              <option key={member.id} value={member.id}>
                {member.name}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Aan">
          <select className="select" value={to} onChange={(event) => setTo(event.target.value)} disabled={existing}>
            {group.members.map((member) => (
              <option key={member.id} value={member.id}>
                {member.name}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div className="field-row" style={{ marginTop: 16 }}>
        <Field label="Bedrag">
          <input
            className="input"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            readOnly={existing}
            autoFocus={!existing}
          />
        </Field>
        <Field label="Datum">
          <input className="input" type="date" value={date} onChange={(event) => setDate(event.target.value)} readOnly={existing} />
        </Field>
      </div>
      {error && (
        <div style={{ marginTop: 16 }}>
          <ErrorBanner message={errorMessage(error)} />
        </div>
      )}
    </Dialog>
  );
}
