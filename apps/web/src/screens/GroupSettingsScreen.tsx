import { parseAmount, type Fine, type GroupDetail, type Member } from "@split/core";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { Button, ConfirmDialog, Dialog, Empty, ErrorBanner, Field, Leader, Person, SkeletonRows } from "../components/ui";
import { amountText } from "../expense/draft";
import { api, errorMessage } from "../lib/api";
import { plain, players } from "../lib/format";
import { keys, useGroup, useGroupMutation } from "../lib/queries";
import { ColorPicker, TeamFields } from "./GroupsScreen";
import { inviteLink } from "./InviteDialog";

export function GroupSettingsScreen() {
  const { groupId = "" } = useParams();
  const group = useGroup(groupId);

  if (group.isPending) {
    return (
      <div className="card">
        <SkeletonRows />
      </div>
    );
  }
  if (group.isError) {
    return (
      <div className="card">
        <Empty title="Dit team is er niet" text={errorMessage(group.error)} />
      </div>
    );
  }
  const team = group.data;

  return (
    <>
      <header className="page-header">
        <div className="page-header__title">
          <Link className="button button--icon" to={`/groups/${team.id}`} aria-label={`Terug naar ${team.name}`}>
            <ArrowLeft size={18} />
          </Link>
          <h1>Instellingen van {team.name}</h1>
        </div>
      </header>

      <SettingsSection title="Team" text="Naam, sport, vereniging en seizoen. Zo herkent iedereen het team in het overzicht.">
        <TeamDetails team={team} />
      </SettingsSection>

      <SettingsSection
        title="Teamkas"
        text="De penningmeester beheert het geld van de teamkas. Inleg en boetes maken teamgenoten over naar het IBAN van de penningmeester."
      >
        <Treasurer team={team} />
      </SettingsSection>

      <SettingsSection
        title="Boetelijst"
        text="De afspraken van jullie team. Bij “Boete geven” kies je uit deze lijst; een eigen boete intypen kan altijd."
      >
        <FineList team={team} />
      </SettingsSection>

      <SettingsSection
        title="Teamleden"
        text="Voeg mensen toe op naam. Ze kunnen later met de uitnodiging hun eigen naam overnemen, maar dat hoeft niet."
      >
        <Members team={team} />
      </SettingsSection>

      <SettingsSection title="Uitnodigen" text="Iedereen met de link of de code kan meedoen met dit team.">
        <Invite team={team} />
      </SettingsSection>

      <SettingsSection
        title="Team verlaten"
        text="Je uitgaven en saldo blijven voor de anderen staan, op jouw naam. Ben je de laatste met een account, dan verdwijnt het team."
      >
        <Leave team={team} />
      </SettingsSection>
    </>
  );
}

function SettingsSection({ title, text, children }: { title: string; text: string; children: React.ReactNode }) {
  return (
    <section className="settings-section">
      <div>
        <h2 className="settings-section__title">{title}</h2>
        <p className="settings-section__text">{text}</p>
      </div>
      <div className="panel">{children}</div>
    </section>
  );
}

function TeamDetails({ team }: { team: GroupDetail }) {
  const [name, setName] = useState(team.name);
  const [color, setColor] = useState(team.color);
  const [sport, setSport] = useState(team.sport);
  const [club, setClub] = useState(team.club ?? "");
  const [season, setSeason] = useState(team.season ?? "");
  const save = useGroupMutation(() => api.updateGroup(team.id, { name, color, sport, club, season }));
  const changed =
    name.trim() !== team.name ||
    color !== team.color ||
    sport !== team.sport ||
    club.trim() !== (team.club ?? "") ||
    season.trim() !== (team.season ?? "");

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        save.mutate(undefined);
      }}
    >
      <Field label="Naam">
        <input className="input" value={name} onChange={(event) => setName(event.target.value)} maxLength={60} />
      </Field>
      <TeamFields sport={sport} club={club} season={season} onSport={setSport} onClub={setClub} onSeason={setSeason} />
      <div className="field">
        <span className="field__label">Markeerstift</span>
        <ColorPicker value={color} onChange={setColor} />
      </div>
      {save.error && (
        <div style={{ marginTop: 16 }}>
          <ErrorBanner message={errorMessage(save.error)} />
        </div>
      )}
      <div className="panel__actions">
        <Button variant="primary" type="submit" disabled={!changed || name.trim() === "" || save.isPending}>
          Opslaan
        </Button>
        {save.isSuccess && !changed && <span className="muted small">Opgeslagen</span>}
      </div>
    </form>
  );
}

function Treasurer({ team }: { team: GroupDetail }) {
  const change = useGroupMutation((memberId: string) => api.updateGroup(team.id, { treasurerMemberId: memberId }));
  const treasurer = team.members.find((m) => m.id === team.treasurerMemberId);
  return (
    <>
      <Field
        label="Penningmeester"
        hint={
          treasurer && !treasurer.iban
            ? `${treasurer.name} heeft nog geen IBAN ingevuld. Dat kan bij Profiel.`
            : "Het IBAN van de penningmeester staat bij de teamkas."
        }
      >
        <select
          className="select"
          value={team.treasurerMemberId ?? ""}
          disabled={change.isPending}
          onChange={(event) => change.mutate(event.target.value)}
        >
          {!team.treasurerMemberId && <option value="">Kies een teamlid</option>}
          {players(team.members).map((member) => (
            <option key={member.id} value={member.id}>
              {member.name}
              {member.id === team.myMemberId ? " (jij)" : ""}
            </option>
          ))}
        </select>
      </Field>
      {change.error && (
        <div style={{ marginTop: 12 }}>
          <ErrorBanner message={errorMessage(change.error)} />
        </div>
      )}
    </>
  );
}

function FineList({ team }: { team: GroupDetail }) {
  const [name, setName] = useState("");
  const [amount, setAmount] = useState("");
  const [editing, setEditing] = useState<Fine | null>(null);
  const add = useGroupMutation(() => {
    const amountCents = parseAmount(amount);
    if (amountCents === null || amountCents <= 0) return Promise.reject(new Error("Vul een geldig bedrag in."));
    return api.addFine(team.id, { name: name.trim(), amountCents });
  });
  const remove = useGroupMutation((fineId: string) => api.removeFine(team.id, fineId));
  const error = add.error ?? remove.error;

  return (
    <>
      {team.fines.length === 0 ? (
        <p className="muted" style={{ marginBottom: 14 }}>
          Nog geen boetes op de lijst.
        </p>
      ) : (
        <ul className="member-list">
          {team.fines.map((fine) => (
            <li key={fine.id}>
              <span className="line" style={{ flex: 1, minWidth: 0 }}>
                <span className="line__name">{fine.name}</span>
                <Leader />
                <span className="line__amount">{plain(fine.amountCents)}</span>
              </span>
              <Button variant="ghost" small icon aria-label={`${fine.name} wijzigen`} onClick={() => setEditing(fine)}>
                <Pencil size={14} />
              </Button>
              <Button
                variant="ghost"
                small
                icon
                aria-label={`${fine.name} van de lijst halen`}
                disabled={remove.isPending}
                onClick={() => remove.mutate(fine.id)}
              >
                <Trash2 size={14} />
              </Button>
            </li>
          ))}
        </ul>
      )}
      <form
        className="inline-form"
        onSubmit={(event) => {
          event.preventDefault();
          add.mutate(undefined, {
            onSuccess: () => {
              setName("");
              setAmount("");
            },
          });
        }}
      >
        <input
          className="input"
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Nieuwe boete"
          aria-label="Naam van de nieuwe boete"
          maxLength={60}
        />
        <input
          className="input input--amount"
          style={{ flex: "0 0 84px" }}
          inputMode="decimal"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          placeholder="0,00"
          aria-label="Bedrag van de nieuwe boete"
        />
        <Button type="submit" disabled={name.trim() === "" || amount.trim() === "" || add.isPending}>
          Toevoegen
        </Button>
      </form>
      {error && (
        <div style={{ marginTop: 12 }}>
          <ErrorBanner message={errorMessage(error)} />
        </div>
      )}
      {editing && <FineDialog team={team} fine={editing} onClose={() => setEditing(null)} />}
    </>
  );
}

function FineDialog({ team, fine, onClose }: { team: GroupDetail; fine: Fine; onClose: () => void }) {
  const [name, setName] = useState(fine.name);
  const [amount, setAmount] = useState(amountText(fine.amountCents));
  const save = useGroupMutation(() => {
    const amountCents = parseAmount(amount);
    if (amountCents === null || amountCents <= 0) return Promise.reject(new Error("Vul een geldig bedrag in."));
    return api.updateFine(team.id, fine.id, { name: name.trim(), amountCents });
  });
  return (
    <Dialog
      title="Boete wijzigen"
      narrow
      onClose={onClose}
      onSubmit={() => save.mutate(undefined, { onSuccess: onClose })}
      footer={
        <>
          <span className="spacer" />
          <Button onClick={onClose}>Annuleren</Button>
          <Button variant="primary" type="submit" disabled={name.trim() === "" || save.isPending}>
            Opslaan
          </Button>
        </>
      }
    >
      <Field label="Naam">
        <input className="input" value={name} onChange={(event) => setName(event.target.value)} maxLength={60} autoFocus />
      </Field>
      <Field label="Bedrag" hint="Geldt voor boetes die je vanaf nu geeft; eerdere boetes veranderen niet.">
        <input className="input" inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} />
      </Field>
      {save.error && (
        <div style={{ marginTop: 12 }}>
          <ErrorBanner message={errorMessage(save.error)} />
        </div>
      )}
    </Dialog>
  );
}

function Members({ team }: { team: GroupDetail }) {
  const [name, setName] = useState("");
  const [renaming, setRenaming] = useState<Member | null>(null);
  const add = useGroupMutation((memberName: string) => api.addMember(team.id, memberName));
  const remove = useGroupMutation((memberId: string) => api.removeMember(team.id, memberId));

  return (
    <>
      <ul className="member-list">
        {players(team.members).map((member) => (
          <li key={member.id}>
            <span className="member-list__name">
              <Person members={team.members} memberId={member.id} />
              {member.id === team.myMemberId && <span className="muted small">(jij)</span>}
              {member.id === team.treasurerMemberId && <span className="pill">penningmeester</span>}
              {member.userId === null && <span className="pill">geen account</span>}
            </span>
            {member.userId === null && (
              <>
                <Button variant="ghost" small icon aria-label={`${member.name} hernoemen`} onClick={() => setRenaming(member)}>
                  <Pencil size={14} />
                </Button>
                <Button
                  variant="ghost"
                  small
                  icon
                  aria-label={`${member.name} verwijderen`}
                  disabled={remove.isPending}
                  onClick={() => remove.mutate(member.id)}
                >
                  <Trash2 size={14} />
                </Button>
              </>
            )}
          </li>
        ))}
      </ul>
      <ErrorBanner message={remove.error ? errorMessage(remove.error) : null} />
      <form
        className="inline-form"
        style={{ marginTop: remove.error ? 12 : 0 }}
        onSubmit={(event) => {
          event.preventDefault();
          add.mutate(name, { onSuccess: () => setName("") });
        }}
      >
        <input
          className="input"
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Naam van het nieuwe teamlid"
          aria-label="Naam van het nieuwe teamlid"
          maxLength={60}
        />
        <Button type="submit" disabled={name.trim() === "" || add.isPending}>
          Teamlid toevoegen
        </Button>
      </form>
      {add.error && (
        <div style={{ marginTop: 12 }}>
          <ErrorBanner message={errorMessage(add.error)} />
        </div>
      )}
      {renaming && <RenameDialog team={team} member={renaming} onClose={() => setRenaming(null)} />}
    </>
  );
}

function RenameDialog({ team, member, onClose }: { team: GroupDetail; member: Member; onClose: () => void }) {
  const [name, setName] = useState(member.name);
  const rename = useGroupMutation(() => api.renameMember(team.id, member.id, name));
  return (
    <Dialog
      title="Teamlid hernoemen"
      narrow
      onClose={onClose}
      onSubmit={() => rename.mutate(undefined, { onSuccess: onClose })}
      footer={
        <>
          <span className="spacer" />
          <Button onClick={onClose}>Annuleren</Button>
          <Button variant="primary" type="submit" disabled={name.trim() === "" || rename.isPending}>
            Opslaan
          </Button>
        </>
      }
    >
      <Field label="Naam">
        <input className="input" value={name} onChange={(event) => setName(event.target.value)} maxLength={60} autoFocus />
      </Field>
      {rename.error && (
        <div style={{ marginTop: 12 }}>
          <ErrorBanner message={errorMessage(rename.error)} />
        </div>
      )}
    </Dialog>
  );
}

function Invite({ team }: { team: GroupDetail }) {
  const [copied, setCopied] = useState(false);
  const link = inviteLink(team.inviteCode);
  return (
    <>
      <p className="invite-code">{team.inviteCode}</p>
      <p className="muted small" style={{ overflowWrap: "anywhere" }}>
        {link}
      </p>
      <div className="panel__actions">
        <Button
          onClick={async () => {
            await navigator.clipboard.writeText(link);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
          }}
        >
          {copied ? "Gekopieerd" : "Link kopiëren"}
        </Button>
      </div>
    </>
  );
}

function Leave({ team }: { team: GroupDetail }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(false);
  const leave = useMutation({
    mutationFn: () => api.leaveGroup(team.id),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: keys.group(team.id) });
      await queryClient.invalidateQueries({ queryKey: keys.groups, exact: true });
      navigate("/");
    },
  });
  return (
    <>
      <Button variant="danger" onClick={() => setConfirming(true)}>
        Team verlaten
      </Button>
      {confirming && (
        <ConfirmDialog
          title={`${team.name} verlaten?`}
          text="Je ziet het team daarna niet meer. Met de uitnodigingscode kun je later weer meedoen."
          confirmLabel="Team verlaten"
          busy={leave.isPending}
          error={leave.error ? errorMessage(leave.error) : null}
          onConfirm={() => leave.mutate()}
          onClose={() => setConfirming(false)}
        />
      )}
    </>
  );
}
