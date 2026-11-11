import type { Expense, GroupDetail, Member, SplitMode } from "@split/core";
import { useMutation } from "@tanstack/react-query";
import { Car, Minus, Plus, ScanLine, Trash2 } from "lucide-react";
import { useMemo, useRef, useState } from "react";
import { Button, ConfirmDialog, Dialog, ErrorBanner, Field, Leader, Person, Segmented } from "../components/ui";
import { api, errorMessage } from "../lib/api";
import { memberColor, money, plain, players } from "../lib/format";
import { useConfig, useInvalidateGroups } from "../lib/queries";
import {
  DEFAULT_RATE_PER_KM,
  applyRide,
  applyScan,
  draftFromExpense,
  evaluateDraft,
  itemsTotalCents,
  newDraft,
  newItem,
  type ExpenseDraft,
} from "./draft";
import { scanReceipt } from "./scan";

const MODES: { value: SplitMode; label: string }[] = [
  { value: "equal", label: "Gelijk" },
  { value: "exact", label: "Bedragen" },
  { value: "shares", label: "Aandelen" },
  { value: "items", label: "Per regel" },
];

/** Add or edit an expense. `expense` is null for a new one. */
export function ExpenseDialog({
  group,
  expense,
  onClose,
}: {
  group: GroupDetail;
  expense: Expense | null;
  onClose: () => void;
}) {
  const invalidate = useInvalidateGroups();
  const config = useConfig();
  const [draft, setDraft] = useState<ExpenseDraft>(() => (expense ? draftFromExpense(expense, group) : newDraft(group)));
  const [showProblem, setShowProblem] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const update = (patch: Partial<ExpenseDraft>) => setDraft((current) => ({ ...current, ...patch }));
  const result = useMemo(() => evaluateDraft(draft, group.members, group.currency), [draft, group]);

  const done = async () => {
    await invalidate();
    onClose();
  };

  const save = useMutation({
    mutationFn: () =>
      expense ? api.updateExpense(group.id, expense.id, result.input!) : api.createExpense(group.id, result.input!),
    onSuccess: done,
  });
  const remove = useMutation({ mutationFn: () => api.deleteExpense(group.id, expense!.id), onSuccess: done });
  const scan = useMutation({
    mutationFn: scanReceipt,
    onSuccess: (receipt) => setDraft((current) => applyScan(current, receipt)),
  });

  const submit = () => {
    if (!result.input) {
      setShowProblem(true);
      return;
    }
    save.mutate();
  };

  return (
    <>
      <Dialog
        title={expense ? "Uitgave bewerken" : "Uitgave toevoegen"}
        onClose={onClose}
        onSubmit={submit}
        footer={
          <>
            {expense && (
              <Button variant="danger" onClick={() => setConfirmDelete(true)}>
                Verwijderen
              </Button>
            )}
            <span className="spacer" />
            <Button onClick={onClose}>Annuleren</Button>
            <Button variant="primary" type="submit" disabled={save.isPending}>
              Opslaan
            </Button>
          </>
        }
      >
        <Field label="Omschrijving">
          <input
            className="input"
            value={draft.description}
            onChange={(event) => update({ description: event.target.value })}
            placeholder="Derde helft, ballen, toernooi…"
            maxLength={120}
            autoFocus={!expense}
          />
        </Field>
        <div className="field-row" style={{ marginTop: 16 }}>
          <Field label="Bedrag">
            <input
              className="input input--figure"
              inputMode="decimal"
              value={draft.amount}
              onChange={(event) => update({ amount: event.target.value })}
              placeholder="0,00"
            />
          </Field>
          <Field label="Datum">
            <input className="input" type="date" value={draft.date} onChange={(event) => update({ date: event.target.value })} />
          </Field>
        </div>
        <RideHelper onApply={(km, rate) => {
          const next = applyRide(draft, km, rate);
          if (next) setDraft(next);
          return next !== null;
        }} />
        <Field label="Betaald door">
          <select className="select" value={draft.paidById} onChange={(event) => update({ paidById: event.target.value })}>
            {group.members.map((member) => (
              <option key={member.id} value={member.id}>
                {member.name}
                {member.id === group.myMemberId ? " (jij)" : ""}
              </option>
            ))}
          </select>
        </Field>

        <div className="split-section">
          <span className="field__label">Verdeling</span>
          <Segmented label="Verdeling" value={draft.mode} options={MODES} onChange={(mode) => update({ mode })} />

          {draft.mode === "equal" && <EqualEditor group={group} draft={draft} update={update} />}
          {draft.mode === "exact" && <ExactEditor group={group} draft={draft} update={update} />}
          {draft.mode === "shares" && <SharesEditor group={group} draft={draft} update={update} />}
          {draft.mode === "items" && (
            <ItemsEditor group={group} draft={draft} update={update}>
              {config.data?.receiptScanEnabled && (
                <>
                  <input
                    ref={fileInput}
                    type="file"
                    accept="image/*"
                    capture="environment"
                    className="visually-hidden"
                    tabIndex={-1}
                    aria-hidden="true"
                    onChange={(event) => {
                      const file = event.target.files?.[0];
                      if (file) scan.mutate(file);
                      event.target.value = "";
                    }}
                  />
                  <Button onClick={() => fileInput.current?.click()} disabled={scan.isPending}>
                    <ScanLine size={16} /> {scan.isPending ? "Bon wordt gelezen…" : "Bon scannen"}
                  </Button>
                </>
              )}
            </ItemsEditor>
          )}
        </div>

        {scan.error && (
          <div style={{ marginTop: 12 }}>
            <ErrorBanner message={errorMessage(scan.error)} />
          </div>
        )}

        {result.shares.length > 0 && (
          <div className="preview" aria-live="polite">
            <p className="preview__title">Per persoon</p>
            {result.shares.map((share) => (
              <div key={share.memberId} className="line">
                <Person members={group.members} memberId={share.memberId} />
                <Leader />
                <span className="line__amount">{plain(share.amountCents)}</span>
              </div>
            ))}
            {result.note && (
              <p className="muted small" style={{ marginTop: 8 }}>
                {result.note}
              </p>
            )}
          </div>
        )}

        {draft.mode === "exact" && result.problem && result.shares.length === 0 && draft.amount.trim() !== "" && (
          <p className="banner banner--note" style={{ marginTop: 16 }}>
            {result.problem}
          </p>
        )}

        <div style={{ marginTop: 16 }}>
          <ErrorBanner message={save.error ? errorMessage(save.error) : showProblem ? result.problem : null} />
        </div>
      </Dialog>

      {confirmDelete && (
        <ConfirmDialog
          title="Uitgave verwijderen?"
          text={`"${expense?.description}" verdwijnt voor het hele team en de saldo's worden opnieuw berekend.`}
          confirmLabel="Verwijderen"
          busy={remove.isPending}
          error={remove.error ? errorMessage(remove.error) : null}
          onConfirm={() => remove.mutate()}
          onClose={() => setConfirmDelete(false)}
        />
      )}
    </>
  );
}

interface EditorProps {
  group: GroupDetail;
  draft: ExpenseDraft;
  update: (patch: Partial<ExpenseDraft>) => void;
}

const MemberLabel = ({ group, member }: { group: GroupDetail; member: Member }) => (
  <span className="split-list__name">
    <Person members={group.members} memberId={member.id} />
  </span>
);

/** Works out the amount of a ride to an away game: kilometres times a rate. */
function RideHelper({ onApply }: { onApply: (kilometres: string, ratePerKm: string) => boolean }) {
  const [open, setOpen] = useState(false);
  const [kilometres, setKilometres] = useState("");
  const [rate, setRate] = useState(DEFAULT_RATE_PER_KM);
  const [invalid, setInvalid] = useState(false);

  if (!open) {
    return (
      <p style={{ margin: "10px 0 18px" }}>
        <button type="button" className="link small" onClick={() => setOpen(true)}>
          <Car size={14} style={{ verticalAlign: "-2px" }} /> Rit berekenen
        </button>
      </p>
    );
  }
  return (
    <div className="ride">
      <Field label="Kilometers">
        <input
          className="input"
          inputMode="decimal"
          value={kilometres}
          onChange={(event) => setKilometres(event.target.value)}
          placeholder="84"
          autoFocus
        />
      </Field>
      <Field label="Per km">
        <input className="input" inputMode="decimal" value={rate} onChange={(event) => setRate(event.target.value)} />
      </Field>
      <Button
        small
        onClick={() => {
          const applied = onApply(kilometres, rate);
          setInvalid(!applied);
          if (applied) setOpen(false);
        }}
      >
        Invullen
      </Button>
      {invalid && <p className="ride__problem small">Vul het aantal kilometers en een bedrag per km in.</p>}
    </div>
  );
}

function EqualEditor({ group, draft, update }: EditorProps) {
  const toggle = (memberId: string) =>
    update({
      equalIds: draft.equalIds.includes(memberId)
        ? draft.equalIds.filter((id) => id !== memberId)
        : [...draft.equalIds, memberId],
    });
  return (
    <ul className="split-list">
      {group.members.map((member) => (
        <li key={member.id}>
          <label>
            <MemberLabel group={group} member={member} />
            <input
              type="checkbox"
              className="checkbox"
              checked={draft.equalIds.includes(member.id)}
              onChange={() => toggle(member.id)}
            />
          </label>
        </li>
      ))}
    </ul>
  );
}

function ExactEditor({ group, draft, update }: EditorProps) {
  return (
    <ul className="split-list">
      {group.members.map((member) => (
        <li key={member.id}>
          <MemberLabel group={group} member={member} />
          <input
            className="input input--amount"
            inputMode="decimal"
            placeholder="0,00"
            aria-label={`Bedrag voor ${member.name}`}
            value={draft.exact[member.id] ?? ""}
            onChange={(event) => update({ exact: { ...draft.exact, [member.id]: event.target.value } })}
          />
        </li>
      ))}
    </ul>
  );
}

function SharesEditor({ group, draft, update }: EditorProps) {
  const set = (memberId: string, weight: number) =>
    update({ weights: { ...draft.weights, [memberId]: Math.max(0, Math.min(99, weight)) } });
  return (
    <ul className="split-list">
      {group.members.map((member) => {
        const weight = draft.weights[member.id] ?? 0;
        return (
          <li key={member.id}>
            <MemberLabel group={group} member={member} />
            <span className="stepper">
              <Button small icon aria-label={`Minder voor ${member.name}`} disabled={weight === 0} onClick={() => set(member.id, weight - 1)}>
                <Minus size={14} />
              </Button>
              <span className="stepper__value" aria-label={`${weight} aandelen`}>
                {weight}
              </span>
              <Button small icon aria-label={`Meer voor ${member.name}`} onClick={() => set(member.id, weight + 1)}>
                <Plus size={14} />
              </Button>
            </span>
          </li>
        );
      })}
    </ul>
  );
}

function ItemsEditor({ group, draft, update, children }: EditorProps & { children?: React.ReactNode }) {
  const allIds = players(group.members).map((m) => m.id);
  const patchItem = (key: string, patch: Partial<ExpenseDraft["items"][number]>) =>
    update({ items: draft.items.map((item) => (item.key === key ? { ...item, ...patch } : item)) });
  const total = itemsTotalCents(draft);

  return (
    <div style={{ marginTop: 12 }}>
      {draft.items.map((item, index) => {
        const everyone = allIds.every((id) => item.memberIds.includes(id));
        return (
          <div key={item.key} className="item">
            <div className="item__fields">
              <input
                className="input"
                placeholder={`Regel ${index + 1}`}
                aria-label={`Naam van regel ${index + 1}`}
                value={item.name}
                maxLength={120}
                onChange={(event) => patchItem(item.key, { name: event.target.value })}
              />
              <input
                className="input input--amount"
                inputMode="decimal"
                placeholder="0,00"
                aria-label={`Bedrag van regel ${index + 1}`}
                value={item.amount}
                onChange={(event) => patchItem(item.key, { amount: event.target.value })}
              />
              <Button
                variant="ghost"
                small
                icon
                aria-label={`Regel ${index + 1} verwijderen`}
                onClick={() => update({ items: draft.items.filter((other) => other.key !== item.key) })}
              >
                <Trash2 size={16} />
              </Button>
            </div>
            <div className="item__swipe" aria-hidden="true">
              {group.members
                .filter((member) => item.memberIds.includes(member.id))
                .map((member) => (
                  <span key={member.id} data-hl={memberColor(group.members, member.id)} />
                ))}
            </div>
            <div className="chips" role="group" aria-label={`Wie had ${item.name || `regel ${index + 1}`}?`}>
              {group.members.map((member) => {
                const active = item.memberIds.includes(member.id);
                return (
                  <button
                    key={member.id}
                    type="button"
                    className={member.kind === "pot" ? "chip chip--plain" : "chip"}
                    data-hl={member.kind === "pot" ? undefined : memberColor(group.members, member.id)}
                    aria-pressed={active}
                    onClick={() =>
                      patchItem(item.key, {
                        memberIds: active ? item.memberIds.filter((id) => id !== member.id) : [...item.memberIds, member.id],
                      })
                    }
                  >
                    {member.name}
                  </button>
                );
              })}
              {allIds.length > 2 && (
                <button
                  type="button"
                  className="chip chip--plain"
                  aria-pressed={everyone}
                  onClick={() => patchItem(item.key, { memberIds: everyone ? [] : allIds })}
                >
                  Iedereen
                </button>
              )}
            </div>
          </div>
        );
      })}
      <div className="scan-bar">
        <Button onClick={() => update({ items: [...draft.items, newItem()] })}>
          <Plus size={16} /> Regel toevoegen
        </Button>
        {children}
        <span className="spacer" />
        {total > 0 && <span className="small">Regels samen {money(total, group.currency)}</span>}
      </div>
    </div>
  );
}
