import type { Expense, GroupDetail, Transfer } from "@split/core";
import { ArrowRight, Check, Copy, Plus, Settings, UserPlus } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { Balance, Button, Empty, ErrorBanner, GroupName, Leader, Mark, Segmented, SkeletonRows, Stamp } from "../components/ui";
import { ExpenseDialog } from "../expense/ExpenseDialog";
import { SettlementDialog, type SettlementDraft } from "../expense/SettlementDialog";
import { errorMessage } from "../lib/api";
import { balanceWords, formatIban, memberColor, memberName, money, monthHeading, myBalanceSentence, plain, shortDate } from "../lib/format";
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

  const openTransfer = (transfer: Transfer) => setOpen({ kind: "settlement", expense: null, draft: { ...transfer } });

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
          <div style={{ minWidth: 0 }}>
            <h1>
              <GroupName name={detail.name} color={detail.color} />
            </h1>
            <p className="small">
              {myBalanceSentence(myBalance, detail.currency)} · {detail.members.length}{" "}
              {detail.members.length === 1 ? "lid" : "leden"} · {money(detail.totalSpentCents, detail.currency)} uitgegeven
            </p>
          </div>
        </div>
        <div className="page-header__actions">
          <Button variant="primary" onClick={() => setOpen({ kind: "expense", expense: null })}>
            <Plus size={18} /> Uitgave toevoegen
          </Button>
          <Button onClick={() => setOpen({ kind: "invite" })} aria-label="Uitnodigen">
            <UserPlus size={18} /> <span className="hide-on-phone">Uitnodigen</span>
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

      {open?.kind === "expense" && <ExpenseDialog group={detail} expense={open.expense} onClose={() => setOpen(null)} />}
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

/** A member's name with their highlighter. */
function Person({ group, memberId }: { group: GroupDetail; memberId: string }) {
  return <Mark color={memberColor(group.members, memberId)}>{memberName(group.members, memberId)}</Mark>;
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
      {months.map((month, index) => (
        <div key={month.heading}>
          {index > 0 && <hr className="perforation" />}
          <p className="rows__heading">{month.heading}</p>
          <ul className="rows">
            {month.entries.map((expense) => {
              const settlement = expense.kind === "settlement";
              const impact = myImpact(expense, group.myMemberId);
              const receiver = expense.shares[0]?.memberId ?? "";
              return (
                <li key={expense.id}>
                  <button type="button" className="row" onClick={() => onOpen(expense)}>
                    <span className="line">
                      <span className="line__name">
                        {settlement
                          ? `${memberName(group.members, expense.paidById)} betaalde ${memberName(group.members, receiver)}`
                          : expense.description}
                      </span>
                      <Leader />
                      <span className="line__amount">{plain(expense.amountCents)}</span>
                    </span>
                    <span className="line line--sub">
                      <span>
                        <Person group={group} memberId={expense.paidById} /> {settlement ? "betaling" : "betaalde"} ·{" "}
                        {shortDate(expense.date)}
                      </span>
                      {settlement ? (
                        <Stamp>Betaald</Stamp>
                      ) : (
                        impact !== 0 && <span>je {balanceWords(impact, group.currency)}</span>
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
  return (
    <section className="card" aria-label="Saldo">
      <div className="card__header">
        <h2>Saldo</h2>
      </div>
      <div style={{ paddingBottom: 14 }}>
        {group.members.map((member) => {
          const cents = group.balances.find((b) => b.memberId === member.id)?.balanceCents ?? 0;
          return (
            <div key={member.id} className="balance-row">
              <span className="balance-row__name">
                <Mark color={memberColor(group.members, member.id)}>{member.name}</Mark>
                {member.id === group.myMemberId && <span className="muted small">(jij)</span>}
                {member.userId === null && <span className="pill">geen account</span>}
              </span>
              <Balance cents={cents} />
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
        <p className="muted" style={{ padding: "4px var(--pad) 22px" }}>
          Iedereen staat quitte.
        </p>
      ) : (
        <div style={{ paddingBottom: 6 }}>
          {group.transfers.map((transfer) => {
            const receiver = group.members.find((m) => m.id === transfer.toMemberId);
            return (
              <div key={`${transfer.fromMemberId}-${transfer.toMemberId}`} className="transfer">
                <div className="transfer__line">
                  <span className="transfer__names">
                    <Person group={group} memberId={transfer.fromMemberId} />
                    <ArrowRight size={14} aria-label="betaalt" />
                    <Person group={group} memberId={transfer.toMemberId} />
                    <span className="spacer" />
                    <span className="amount">{plain(transfer.amountCents)}</span>
                  </span>
                  <Button small onClick={() => onPay(transfer)}>
                    Markeer als betaald
                  </Button>
                </div>
                {receiver?.iban && <IbanButton iban={receiver.iban} />}
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
