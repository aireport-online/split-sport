import type { Expense, GroupDetail, Transfer } from "@split/core";
import { ArrowRight, Check, Copy, Plus, Settings, UserPlus } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { Avatar, Balance, Button, Empty, ErrorBanner, Money, Segmented, SkeletonRows, Tile } from "../components/ui";
import { ExpenseDialog } from "../expense/ExpenseDialog";
import { SettlementDialog, type SettlementDraft } from "../expense/SettlementDialog";
import { errorMessage } from "../lib/api";
import { balanceWords, formatIban, memberColor, memberName, money, monthHeading, myBalanceSentence, shortDate } from "../lib/format";
import { useExpenses, useGroup } from "../lib/queries";
import { InviteDialog } from "./InviteDialog";

type Tab = "expenses" | "balance";

type Open =
  | { kind: "expense"; expense: Expense | null }
  | { kind: "settlement"; draft: SettlementDraft; expense: Expense | null }
  | { kind: "invite" }
  | null;

export function GroupScreen() {
  const { groupId = "" } = useParams();
  const group = useGroup(groupId);
  const expenses = useExpenses(groupId);
  const [tab, setTab] = useState<Tab>("expenses");
  const [open, setOpen] = useState<Open>(null);

  if (group.isPending) {
    return (
      <div className="card">
        <SkeletonRows count={4} />
      </div>
    );
  }
  if (group.isError) {
    return (
      <div className="card">
        <Empty title="Deze groep is er niet" text={errorMessage(group.error)}>
          <Link className="button" to="/">
            Naar je groepen
          </Link>
        </Empty>
      </div>
    );
  }

  const detail = group.data;
  const myBalance = detail.balances.find((b) => b.memberId === detail.myMemberId)?.balanceCents ?? 0;

  const openTransfer = (transfer: Transfer) =>
    setOpen({ kind: "settlement", expense: null, draft: { ...transfer } });

  const openEntry = (expense: Expense) =>
    expense.kind === "settlement"
      ? setOpen({
          kind: "settlement",
          expense,
          draft: {
            fromMemberId: expense.paidById,
            toMemberId: expense.shares[0]?.memberId ?? detail.myMemberId,
            amountCents: expense.amountCents,
            date: expense.date,
          },
        })
      : setOpen({ kind: "expense", expense });

  return (
    <>
      <header className="page-header">
        <div className="page-header__title">
          <Tile name={detail.name} color={detail.color} size="large" />
          <div style={{ minWidth: 0 }}>
            <h1>{detail.name}</h1>
            <p className="muted small">
              {myBalanceSentence(myBalance, detail.currency)} · {detail.members.length}{" "}
              {detail.members.length === 1 ? "lid" : "leden"} · {money(detail.totalSpentCents, detail.currency)} uitgegeven
            </p>
          </div>
        </div>
        <div className="page-header__actions">
          <Button variant="primary" onClick={() => setOpen({ kind: "expense", expense: null })}>
            <Plus size={16} /> Uitgave toevoegen
          </Button>
          <Button onClick={() => setOpen({ kind: "invite" })} aria-label="Uitnodigen">
            <UserPlus size={16} /> <span className="hide-on-phone">Uitnodigen</span>
          </Button>
          <Link className="button button--icon" to={`/groups/${detail.id}/settings`} aria-label="Groepsinstellingen" title="Groepsinstellingen">
            <Settings size={18} />
          </Link>
        </div>
      </header>

      <div className="tabs">
        <Segmented<Tab>
          label="Onderdeel"
          value={tab}
          onChange={setTab}
          options={[
            { value: "expenses", label: "Uitgaven" },
            { value: "balance", label: "Saldo" },
          ]}
        />
      </div>

      <div className="columns" data-tab={tab}>
        <section className="card columns__main" aria-label="Uitgaven">
          <div className="card__header">
            <h2>Uitgaven</h2>
          </div>
          {expenses.isPending && <SkeletonRows />}
          {expenses.isError && (
            <div className="card__body">
              <ErrorBanner message={errorMessage(expenses.error)} />
            </div>
          )}
          {expenses.isSuccess && expenses.data.length === 0 && (
            <Empty title="Nog geen uitgaven" text="Voeg de eerste toe. Daarna zie je hier wie wat betaald heeft.">
              <Button variant="primary" onClick={() => setOpen({ kind: "expense", expense: null })}>
                Uitgave toevoegen
              </Button>
            </Empty>
          )}
          {expenses.isSuccess && expenses.data.length > 0 && (
            <ExpenseList group={detail} expenses={expenses.data} onOpen={openEntry} />
          )}
        </section>

        <div className="stack columns__side">
          <BalanceCard group={detail} />
          <SettleCard group={detail} onPay={openTransfer} />
        </div>
      </div>

      {open?.kind === "expense" && (
        <ExpenseDialog group={detail} expense={open.expense} onClose={() => setOpen(null)} />
      )}
      {open?.kind === "settlement" && (
        <SettlementDialog group={detail} draft={open.draft} expense={open.expense} onClose={() => setOpen(null)} />
      )}
      {open?.kind === "invite" && <InviteDialog group={detail} onClose={() => setOpen(null)} />}
    </>
  );
}

/** What an entry means for the signed-in member: lent out (+) or owed (−). */
function myImpact(expense: Expense, myMemberId: string): number {
  const paid = expense.paidById === myMemberId ? expense.amountCents : 0;
  const owed = expense.shares.find((s) => s.memberId === myMemberId)?.amountCents ?? 0;
  return paid - owed;
}

function ExpenseList({
  group,
  expenses,
  onOpen,
}: {
  group: GroupDetail;
  expenses: Expense[];
  onOpen: (expense: Expense) => void;
}) {
  const months: { heading: string; entries: Expense[] }[] = [];
  for (const expense of expenses) {
    const heading = monthHeading(expense.date);
    const last = months[months.length - 1];
    if (last?.heading === heading) last.entries.push(expense);
    else months.push({ heading, entries: [expense] });
  }

  return (
    <>
      {months.map((month) => (
        <div key={month.heading}>
          <p className="rows__heading">{month.heading}</p>
          <ul className="rows">
            {month.entries.map((expense) => {
              const payer = memberName(group.members, expense.paidById);
              const settlement = expense.kind === "settlement";
              const impact = myImpact(expense, group.myMemberId);
              return (
                <li key={expense.id}>
                  <button type="button" className="row" onClick={() => onOpen(expense)}>
                    <Avatar name={payer} color={memberColor(group.members, expense.paidById)} />
                    <span className="row__main">
                      <span className="row__title">
                        {settlement
                          ? `${payer} betaalde ${memberName(group.members, expense.shares[0]?.memberId ?? "")}`
                          : expense.description}
                      </span>
                      <span className="row__sub">
                        {settlement ? "Betaling" : `${payer} betaalde`} · {shortDate(expense.date)}
                      </span>
                    </span>
                    <span className="row__end">
                      <Money cents={expense.amountCents} currency={group.currency} />
                      {!settlement && impact !== 0 && (
                        <span className="row__sub" style={impact > 0 ? { color: "var(--positive)" } : undefined}>
                          je {balanceWords(impact, group.currency)}
                        </span>
                      )}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </>
  );
}

function BalanceCard({ group }: { group: GroupDetail }) {
  const largest = Math.max(1, ...group.balances.map((b) => Math.abs(b.balanceCents)));
  return (
    <section className="card" aria-label="Saldo">
      <div className="card__header">
        <h2>Saldo</h2>
      </div>
      <div style={{ paddingBottom: 12 }}>
        {group.members.map((member) => {
          const cents = group.balances.find((b) => b.memberId === member.id)?.balanceCents ?? 0;
          const width = `${(Math.abs(cents) / largest) * 50}%`;
          return (
            <div key={member.id} className="balance-row">
              <Avatar name={member.name} color={memberColor(group.members, member.id)} small />
              <span className="balance-row__name">
                {member.name}
                {member.id === group.myMemberId && <span className="muted"> (jij)</span>}
                {member.userId === null && (
                  <>
                    {" "}
                    <span className="pill">geen account</span>
                  </>
                )}
              </span>
              <Balance cents={cents} currency={group.currency} />
              <span className="balance-bar" aria-hidden="true">
                {cents !== 0 && (
                  <span
                    className={`balance-bar__fill${cents > 0 ? " balance-bar__fill--positive" : ""}`}
                    style={cents > 0 ? { left: "50%", width } : { right: "50%", width }}
                  />
                )}
              </span>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function SettleCard({ group, onPay }: { group: GroupDetail; onPay: (transfer: Transfer) => void }) {
  return (
    <section className="card" aria-label="Vereffenen">
      <div className="card__header">
        <h2>Vereffenen</h2>
      </div>
      {group.transfers.length === 0 ? (
        <p className="muted" style={{ padding: "4px var(--pad) 24px" }}>
          Iedereen staat quitte.
        </p>
      ) : (
        <div style={{ paddingBottom: 8 }}>
          {group.transfers.map((transfer) => {
            const receiver = group.members.find((m) => m.id === transfer.toMemberId);
            return (
              <div key={`${transfer.fromMemberId}-${transfer.toMemberId}`} className="transfer">
                <div className="transfer__line">
                  <span className="transfer__names">
                    {memberName(group.members, transfer.fromMemberId)} <ArrowRight aria-label="betaalt" />{" "}
                    {memberName(group.members, transfer.toMemberId)}
                  </span>
                  <Money cents={transfer.amountCents} currency={group.currency} />
                </div>
                <div className="transfer__actions">
                  <Button small onClick={() => onPay(transfer)}>
                    Markeer als betaald
                  </Button>
                  {receiver?.iban && <IbanButton iban={receiver.iban} />}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

function IbanButton({ iban }: { iban: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(iban);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button type="button" className="iban" onClick={copy} title="IBAN kopiëren">
      {copied ? <Check /> : <Copy />}
      {copied ? "Gekopieerd" : formatIban(iban)}
    </button>
  );
}
