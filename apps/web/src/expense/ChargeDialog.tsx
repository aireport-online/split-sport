import { parseAmount, type Expense, type GroupDetail } from "@split/core";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Button, Dialog, ErrorBanner, Field, Leader, Person } from "../components/ui";
import { api, errorMessage } from "../lib/api";
import { memberColor, money, plain, players, shortDate, todayIso } from "../lib/format";
import { useInvalidateGroups } from "../lib/queries";
import { amountText } from "./draft";

export type ChargeType = "fine" | "contribution";

const TEXT = {
  fine: { title: "Boete geven", who: "Wie krijgt de boete?", amount: "Bedrag", save: "Boete geven", placeholder: "Waarvoor?" },
  contribution: {
    title: "Inleg vragen",
    who: "Wie legt in?",
    amount: "Bedrag per persoon",
    save: "Inleg vragen",
    placeholder: "Inleg november",
  },
} as const;

/**
 * Charges team members for the pot: a fine for one or a few of them, or a
 * contribution that everyone is asked for. Nothing is paid yet; they owe the pot.
 */
export function ChargeDialog({ team, type, onClose }: { team: GroupDetail; type: ChargeType; onClose: () => void }) {
  const invalidate = useInvalidateGroups();
  const people = players(team.members);
  const text = TEXT[type];
  const [memberIds, setMemberIds] = useState<string[]>(() => (type === "contribution" ? people.map((m) => m.id) : []));
  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [date, setDate] = useState(todayIso());
  const [fineId, setFineId] = useState<string | null>(null);

  const amountCents = parseAmount(amount);
  const count = memberIds.length;

  const save = useMutation({
    mutationFn: async () => {
      if (count === 0) throw new Error(type === "fine" ? "Kies wie de boete krijgt." : "Kies wie er inlegt.");
      if (description.trim() === "") {
        throw new Error(type === "fine" ? "Kies een boete of vul in waarvoor." : "Geef de inleg een omschrijving.");
      }
      if (amountCents === null || amountCents <= 0) throw new Error("Vul een geldig bedrag in.");
      // In the order of the team, so the entry reads the same for everyone.
      const ordered = people.map((m) => m.id).filter((id) => memberIds.includes(id));
      await api.createCharge(team.id, { type, description: description.trim(), amountCents, memberIds: ordered, date });
    },
    onSuccess: async () => {
      await invalidate();
      onClose();
    },
  });

  const toggle = (memberId: string) =>
    setMemberIds((current) => (current.includes(memberId) ? current.filter((id) => id !== memberId) : [...current, memberId]));

  return (
    <Dialog
      title={text.title}
      onClose={onClose}
      onSubmit={() => save.mutate()}
      footer={
        <>
          <span className="spacer" />
          <Button onClick={onClose}>Annuleren</Button>
          <Button variant="primary" type="submit" disabled={save.isPending}>
            {text.save}
          </Button>
        </>
      }
    >
      <div className="field">
        <span className="field__label">{text.who}</span>
        <div className="chips" role="group" aria-label={text.who} style={{ marginTop: 6 }}>
          {people.map((member) => (
            <button
              key={member.id}
              type="button"
              className="chip"
              data-hl={memberColor(team.members, member.id)}
              aria-pressed={memberIds.includes(member.id)}
              onClick={() => toggle(member.id)}
            >
              {member.name}
            </button>
          ))}
        </div>
      </div>

      {type === "fine" && team.fines.length > 0 && (
        <div className="field">
          <span className="field__label">Boetelijst</span>
          <div className="chips" role="group" aria-label="Boetelijst" style={{ marginTop: 6 }}>
            {team.fines.map((fine) => (
              <button
                key={fine.id}
                type="button"
                className="chip chip--plain chip--fine"
                aria-pressed={fineId === fine.id}
                onClick={() => {
                  setFineId(fine.id);
                  setDescription(fine.name);
                  setAmount(amountText(fine.amountCents));
                }}
              >
                {fine.name} <span className="chip__amount">{plain(fine.amountCents)}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      <Field label="Omschrijving">
        <input
          className="input"
          value={description}
          onChange={(event) => {
            setDescription(event.target.value);
            setFineId(null);
          }}
          placeholder={text.placeholder}
          maxLength={120}
        />
      </Field>
      <div className="field-row" style={{ marginTop: 18 }}>
        <Field label={text.amount}>
          <input
            className="input input--figure"
            inputMode="decimal"
            value={amount}
            onChange={(event) => {
              setAmount(event.target.value);
              setFineId(null);
            }}
            placeholder="0,00"
          />
        </Field>
        <Field label="Datum">
          <input className="input" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        </Field>
      </div>

      {amountCents !== null && amountCents > 0 && count > 0 && (
        <div className="preview" aria-live="polite">
          <div className="line">
            <span>
              {count} × {money(amountCents, team.currency)}
            </span>
            <Leader />
            <span className="line__amount">{money(amountCents * count, team.currency)}</span>
          </div>
          <p className="muted small" style={{ marginTop: 6 }}>
            Komt bij wat {count === 1 ? "dit teamlid" : "deze teamleden"} nog aan de teamkas moet{count === 1 ? "" : "en"} betalen.
          </p>
        </div>
      )}

      {save.error && (
        <div style={{ marginTop: 16 }}>
          <ErrorBanner message={errorMessage(save.error)} />
        </div>
      )}
    </Dialog>
  );
}

/** A recorded fine or contribution. It cannot be changed, only removed and given again. */
export function ChargeDetailDialog({ team, entry, onClose }: { team: GroupDetail; entry: Expense; onClose: () => void }) {
  const invalidate = useInvalidateGroups();
  const remove = useMutation({
    mutationFn: () => api.deleteExpense(team.id, entry.id),
    onSuccess: async () => {
      await invalidate();
      onClose();
    },
  });
  const fine = entry.kind === "fine";

  return (
    <Dialog
      title={fine ? "Boete" : "Inleg"}
      narrow
      onClose={onClose}
      footer={
        <>
          <Button variant="danger" onClick={() => remove.mutate()} disabled={remove.isPending}>
            {fine ? "Boete verwijderen" : "Inleg verwijderen"}
          </Button>
          <span className="spacer" />
          <Button onClick={onClose}>Sluiten</Button>
        </>
      }
    >
      <p style={{ fontSize: 17 }}>{entry.description}</p>
      <p className="muted small" style={{ marginTop: 2 }}>
        {shortDate(entry.date)}
      </p>
      <div className="preview">
        {entry.shares.map((share) => (
          <div key={share.memberId} className="line">
            <Person members={team.members} memberId={share.memberId} />
            <Leader />
            <span className="line__amount">{plain(share.amountCents)}</span>
          </div>
        ))}
      </div>
      {remove.error && (
        <div style={{ marginTop: 16 }}>
          <ErrorBanner message={errorMessage(remove.error)} />
        </div>
      )}
    </Dialog>
  );
}
