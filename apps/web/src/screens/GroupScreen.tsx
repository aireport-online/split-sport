import type { Expense, GroupDetail, Transfer } from "@split/core";
import { ArrowRight, Check, Copy, Plus, Settings, UserPlus } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { Balance, Button, Empty, ErrorBanner, GroupName, Leader, Person, Segmented, SkeletonRows, Stamp } from "../components/ui";
import { ChargeDialog, ChargeDetailDialog, type ChargeType } from "../expense/ChargeDialog";
import { ExpenseDialog } from "../expense/ExpenseDialog";
import { SettlementDialog, type SettlementDraft } from "../expense/SettlementDialog";
import { errorMessage } from "../lib/api";
import {
  balanceWords,
  formatIban,
  memberName,
  money,
  monthHeading,
  myBalanceSentence,
  plain,
  players,
  shortDate,
  signedMoney,
  teamLine,
  teamMembers,
} from "../lib/format";
import { useExpenses, useGroup } from "../lib/queries";
import { InviteDialog } from "./InviteDialog";

type Tab = "expenses" | "pot" | "balance";

type Open =
  | { kind: "expense"; expense: Expense | null }
  | { kind: "settlement"; draft: SettlementDraft; expense: Expense | null }
  | { kind: "charge"; type: ChargeType }
  | { kind: "charge-detail"; expense: Expense }
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
        <Empty title="Dit team is er niet" text={errorMessage(group.error)}>
          <Link className="button" to="/">
            Naar je teams
          </Link>
        </Empty>
      </div>
    );
  }

  const team = group.data;
  const myBalance = team.balances.find((b) => b.memberId === team.myMemberId)?.balanceCents ?? 0;

  const openTransfer = (transfer: Transfer) => setOpen({ kind: "settlement", expense: null, draft: { ...transfer } });

  const openEntry = (expense: Expense) => {
    if (expense.kind === "expense") return setOpen({ kind: "expense", expense });
    if (expense.kind === "fine" || expense.kind === "contribution") return setOpen({ kind: "charge-detail", expense });
    setOpen({
      kind: "settlement",
      expense,
      draft: {
        fromMemberId: expense.paidById,
        toMemberId: expense.shares[0]?.memberId ?? team.myMemberId,
        amountCents: expense.amountCents,
        date: expense.date,
      },
    });
  };

  return (
    <>
      <header className="page-header">
        <div className="page-header__title">
          <div style={{ minWidth: 0 }}>
            <h1>
              <GroupName name={team.name} color={team.color} />
            </h1>
            <p className="small">{teamLine(team)}</p>
            <p className="small muted" style={{ marginTop: 2 }}>
              {myBalanceSentence(myBalance, team.currency)} · {teamMembers(players(team.members).length)} ·{" "}
              {money(team.totalSpentCents, team.currency)} uitgegeven
            </p>
          </div>
        </div>
        <div className="page-header__actions">
          <Button variant="primary" onClick={() => setOpen({ kind: "expense", expense: null })}>
            <Plus size={18} /> Uitgave toevoegen
          </Button>
          <Button onClick={() => setOpen({ kind: "charge", type: "fine" })}>Boete geven</Button>
          <Button onClick={() => setOpen({ kind: "charge", type: "contribution" })}>Inleg vragen</Button>
          <Button icon onClick={() => setOpen({ kind: "invite" })} aria-label="Uitnodigen" title="Uitnodigen">
            <UserPlus size={18} />
          </Button>
          <Link className="button button--icon" to={`/groups/${team.id}/settings`} aria-label="Teaminstellingen" title="Teaminstellingen">
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
            { value: "pot", label: "Teamkas" },
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
            <Empty title="Nog niets genoteerd" text="Vraag de eerste inleg of voeg een uitgave toe.">
              <Button variant="primary" onClick={() => setOpen({ kind: "charge", type: "contribution" })}>
                Inleg vragen
              </Button>
              <Button onClick={() => setOpen({ kind: "expense", expense: null })}>Uitgave toevoegen</Button>
            </Empty>
          )}
          {expenses.isSuccess && expenses.data.length > 0 && <EntryList team={team} entries={expenses.data} onOpen={openEntry} />}
        </section>

        <div className="stack columns__side">
          <PotCard team={team} onPay={openTransfer} />
          <BalanceCard team={team} />
          <SettleCard team={team} onPay={openTransfer} />
          <FineRanking team={team} />
        </div>
      </div>

      {open?.kind === "expense" && <ExpenseDialog group={team} expense={open.expense} onClose={() => setOpen(null)} />}
      {open?.kind === "settlement" && (
        <SettlementDialog group={team} draft={open.draft} expense={open.expense} onClose={() => setOpen(null)} />
      )}
      {open?.kind === "charge" && <ChargeDialog team={team} type={open.type} onClose={() => setOpen(null)} />}
      {open?.kind === "charge-detail" && <ChargeDetailDialog team={team} entry={open.expense} onClose={() => setOpen(null)} />}
      {open?.kind === "invite" && <InviteDialog group={team} onClose={() => setOpen(null)} />}
    </>
  );
}

/** What an expense means for the signed-in member: lent out (+) or owed (−). */
function myImpact(expense: Expense, myMemberId: string): number {
  const paid = expense.paidById === myMemberId ? expense.amountCents : 0;
  const owed = expense.shares.find((s) => s.memberId === myMemberId)?.amountCents ?? 0;
  return paid - owed;
}

function EntryList({ team, entries, onOpen }: { team: GroupDetail; entries: Expense[]; onOpen: (entry: Expense) => void }) {
  const months: { heading: string; entries: Expense[] }[] = [];
  for (const entry of entries) {
    const heading = monthHeading(entry.date);
    const last = months[months.length - 1];
    if (last?.heading === heading) last.entries.push(entry);
    else months.push({ heading, entries: [entry] });
  }

  return (
    <>
      {months.map((month, index) => (
        <div key={month.heading}>
          {index > 0 && <hr className="perforation" />}
          <p className="rows__heading">{month.heading}</p>
          <ul className="rows">
            {month.entries.map((entry) => (
              <li key={entry.id}>
                <button type="button" className="row" onClick={() => onOpen(entry)}>
                  <EntryRow team={team} entry={entry} />
                </button>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </>
  );
}

/** One line of the list: an expense, a fine, a contribution or a payment. */
function EntryRow({ team, entry }: { team: GroupDetail; entry: Expense }) {
  const date = shortDate(entry.date);
  const title =
    entry.kind === "settlement"
      ? `${memberName(team.members, entry.paidById)} betaalde ${memberName(team.members, entry.shares[0]?.memberId ?? "")}`
      : entry.description;
  const impact = myImpact(entry, team.myMemberId);
  const perMember = entry.shares[0]?.amountCents ?? 0;
  const sameForAll = entry.shares.every((share) => share.amountCents === perMember);

  return (
    <>
      <span className="line">
        <span className="line__name">{title}</span>
        <Leader />
        <span className="line__amount">{plain(entry.amountCents)}</span>
      </span>
      <span className="line line--sub">
        {entry.kind === "expense" && (
          <>
            <span>
              <Person members={team.members} memberId={entry.paidById} /> betaalde · {date}
            </span>
            {impact !== 0 && <span>je {balanceWords(impact, team.currency)}</span>}
          </>
        )}
        {entry.kind === "settlement" && (
          <>
            <span>
              <Person members={team.members} memberId={entry.paidById} /> betaling · {date}
            </span>
            <Stamp>Betaald</Stamp>
          </>
        )}
        {entry.kind === "fine" && (
          <>
            <span className="line__people">
              {entry.shares.map((share) => (
                <Person key={share.memberId} members={team.members} memberId={share.memberId} />
              ))}
              <span>· {date}</span>
            </span>
            <Stamp>Boete</Stamp>
          </>
        )}
        {entry.kind === "contribution" && (
          <>
            <span>
              {sameForAll ? `${entry.shares.length} × ${plain(perMember)}` : `${entry.shares.length} teamleden`} · {date}
            </span>
            <Stamp ink>Inleg</Stamp>
          </>
        )}
      </span>
    </>
  );
}

function TransferRow({ team, transfer, onPay }: { team: GroupDetail; transfer: Transfer; onPay: (transfer: Transfer) => void }) {
  return (
    <div className="transfer">
      <div className="transfer__line">
        <span className="transfer__names">
          <Person members={team.members} memberId={transfer.fromMemberId} />
          <ArrowRight size={14} aria-label="betaalt" />
          <Person members={team.members} memberId={transfer.toMemberId} />
          <span className="spacer" />
          <span className="amount">{plain(transfer.amountCents)}</span>
        </span>
        <Button small onClick={() => onPay(transfer)}>
          Markeer als betaald
        </Button>
      </div>
    </div>
  );
}

const involvesPot = (team: GroupDetail, transfer: Transfer) =>
  transfer.fromMemberId === team.potMemberId || transfer.toMemberId === team.potMemberId;

/** The team pot: what is in it, what is still coming and going, and who keeps it. */
function PotCard({ team, onPay }: { team: GroupDetail; onPay: (transfer: Transfer) => void }) {
  const treasurer = team.members.find((m) => m.id === team.treasurerMemberId);
  const transfers = team.transfers.filter((transfer) => involvesPot(team, transfer));
  return (
    <section className="card side--pot" aria-label="Teamkas">
      <div className="card__header">
        <h2>Teamkas</h2>
      </div>
      <div style={{ padding: "0 var(--pad) 12px" }}>
        <p className="muted small">Kasstand</p>
        <p className="figure">{team.pot.cashCents < 0 ? signedMoney(team.pot.cashCents) : money(team.pot.cashCents)}</p>
        <div className="total__split">
          <div className="line">
            <span>Nog te ontvangen</span>
            <Leader />
            <span className="line__amount">{money(team.pot.toReceiveCents)}</span>
          </div>
          <div className="line">
            <span>Nog uit te betalen</span>
            <Leader />
            <span className="line__amount">{money(team.pot.toPayCents)}</span>
          </div>
        </div>
      </div>
      <hr className="perforation" />
      <div style={{ padding: "8px var(--pad) 10px" }}>
        <p className="muted small">Penningmeester</p>
        {treasurer ? (
          <p style={{ marginTop: 2 }}>
            <Person members={team.members} memberId={treasurer.id} />{" "}
            {treasurer.iban ? <IbanButton iban={treasurer.iban} /> : <span className="muted small">nog geen IBAN ingevuld</span>}
          </p>
        ) : (
          <p className="muted small">Nog niemand. Kies er een bij de teaminstellingen.</p>
        )}
      </div>
      <hr className="perforation" />
      {transfers.length === 0 ? (
        <p className="muted" style={{ padding: "10px var(--pad) 20px" }}>
          Niemand heeft nog iets openstaan bij de teamkas.
        </p>
      ) : (
        <div style={{ paddingBottom: 6 }}>
          {transfers.map((transfer) => (
            <TransferRow key={`${transfer.fromMemberId}-${transfer.toMemberId}`} team={team} transfer={transfer} onPay={onPay} />
          ))}
        </div>
      )}
    </section>
  );
}

/** What team members have among each other, apart from the pot. */
function BalanceCard({ team }: { team: GroupDetail }) {
  return (
    <section className="card side--balance" aria-label="Saldo onderling">
      <div className="card__header">
        <h2>Saldo</h2>
        <span className="muted small">onderling</span>
      </div>
      <div style={{ paddingBottom: 14 }}>
        {players(team.members).map((member) => {
          const cents = team.balances.find((b) => b.memberId === member.id)?.peerCents ?? 0;
          return (
            <div key={member.id} className="balance-row">
              <span className="balance-row__name">
                <Person members={team.members} memberId={member.id} />
                {member.id === team.myMemberId && <span className="muted small">(jij)</span>}
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

function SettleCard({ team, onPay }: { team: GroupDetail; onPay: (transfer: Transfer) => void }) {
  const transfers = team.transfers.filter((transfer) => !involvesPot(team, transfer));
  return (
    <section className="card side--balance" aria-label="Vereffenen">
      <div className="card__header">
        <h2>Vereffenen</h2>
      </div>
      {transfers.length === 0 ? (
        <p className="muted" style={{ padding: "4px var(--pad) 22px" }}>
          Iedereen staat quitte.
        </p>
      ) : (
        <div style={{ paddingBottom: 6 }}>
          {transfers.map((transfer) => (
            <TransferRow key={`${transfer.fromMemberId}-${transfer.toMemberId}`} team={team} transfer={transfer} onPay={onPay} />
          ))}
        </div>
      )}
    </section>
  );
}

/** Who collected the most fines. Hidden while nobody has one. */
function FineRanking({ team }: { team: GroupDetail }) {
  if (team.fineStats.length === 0) return null;
  return (
    <section className="card side--balance" aria-label="Boetekoning">
      <div className="card__header">
        <h2>Boetekoning</h2>
      </div>
      <div style={{ padding: "0 var(--pad) 16px" }}>
        {team.fineStats.map((stat, index) => (
          <div key={stat.memberId} className="line" style={{ padding: "5px 0" }}>
            <span className="line__people">
              <Person members={team.members} memberId={stat.memberId} />
              <span className="muted small">{stat.count}×</span>
              {index === 0 && <Stamp>Koning</Stamp>}
            </span>
            <Leader />
            <span className="line__amount">{plain(stat.totalCents)}</span>
          </div>
        ))}
      </div>
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
