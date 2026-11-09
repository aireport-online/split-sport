import type { GroupDetail, Member } from "@split/core";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { Avatar, Button, ConfirmDialog, Dialog, Empty, ErrorBanner, Field, SkeletonRows } from "../components/ui";
import { api, errorMessage } from "../lib/api";
import { memberColor } from "../lib/format";
import { keys, useGroup, useGroupMutation } from "../lib/queries";
import { ColorPicker } from "./GroupsScreen";
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
        <Empty title="Deze groep is er niet" text={errorMessage(group.error)} />
      </div>
    );
  }
  const detail = group.data;

  return (
    <>
      <header className="page-header">
        <div className="page-header__title">
          <Link className="button button--icon" to={`/groups/${detail.id}`} aria-label={`Terug naar ${detail.name}`}>
            <ArrowLeft size={18} />
          </Link>
          <h1>Instellingen van {detail.name}</h1>
        </div>
      </header>

      <SettingsSection title="Naam en kleur" text="Zo herkent iedereen de groep in het overzicht.">
        <NameAndColor group={detail} />
      </SettingsSection>

      <SettingsSection
        title="Leden"
        text="Voeg mensen toe op naam. Ze kunnen later met de uitnodiging hun eigen naam overnemen, maar dat hoeft niet."
      >
        <Members group={detail} />
      </SettingsSection>

      <SettingsSection title="Uitnodigen" text="Iedereen met de link of de code kan meedoen met deze groep.">
        <Invite group={detail} />
      </SettingsSection>

      <SettingsSection
        title="Groep verlaten"
        text="Je uitgaven en saldo blijven voor de anderen staan, op jouw naam. Ben je de laatste met een account, dan verdwijnt de groep."
      >
        <Leave group={detail} />
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

function NameAndColor({ group }: { group: GroupDetail }) {
  const [name, setName] = useState(group.name);
  const [color, setColor] = useState(group.color);
  const save = useGroupMutation(() => api.updateGroup(group.id, { name, color }));
  const changed = name.trim() !== group.name || color !== group.color;

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
      <div className="field">
        <span className="field__label">Kleur</span>
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

function Members({ group }: { group: GroupDetail }) {
  const [name, setName] = useState("");
  const [renaming, setRenaming] = useState<Member | null>(null);
  const add = useGroupMutation((memberName: string) => api.addMember(group.id, memberName));
  const remove = useGroupMutation((memberId: string) => api.removeMember(group.id, memberId));

  return (
    <>
      <ul className="member-list">
        {group.members.map((member) => (
          <li key={member.id}>
            <Avatar name={member.name} color={memberColor(group.members, member.id)} small />
            <span className="member-list__name">
              {member.name}
              {member.id === group.myMemberId && <span className="muted"> (jij)</span>}
            </span>
            {member.userId === null && (
              <>
                <span className="pill">geen account</span>
                <Button variant="ghost" small aria-label={`${member.name} hernoemen`} onClick={() => setRenaming(member)}>
                  <Pencil size={14} />
                </Button>
                <Button
                  variant="ghost"
                  small
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
          placeholder="Naam van het nieuwe lid"
          aria-label="Naam van het nieuwe lid"
          maxLength={60}
        />
        <Button type="submit" disabled={name.trim() === "" || add.isPending}>
          Lid toevoegen
        </Button>
      </form>
      {add.error && (
        <div style={{ marginTop: 12 }}>
          <ErrorBanner message={errorMessage(add.error)} />
        </div>
      )}
      {renaming && <RenameDialog group={group} member={renaming} onClose={() => setRenaming(null)} />}
    </>
  );
}

function RenameDialog({ group, member, onClose }: { group: GroupDetail; member: Member; onClose: () => void }) {
  const [name, setName] = useState(member.name);
  const rename = useGroupMutation(() => api.renameMember(group.id, member.id, name));
  return (
    <Dialog
      title="Lid hernoemen"
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

function Invite({ group }: { group: GroupDetail }) {
  const [copied, setCopied] = useState(false);
  const link = inviteLink(group.inviteCode);
  return (
    <>
      <p className="invite-code">{group.inviteCode}</p>
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

function Leave({ group }: { group: GroupDetail }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(false);
  const leave = useMutation({
    mutationFn: () => api.leaveGroup(group.id),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: keys.group(group.id) });
      await queryClient.invalidateQueries({ queryKey: keys.groups, exact: true });
      navigate("/");
    },
  });
  return (
    <>
      <Button variant="danger" onClick={() => setConfirming(true)}>
        Groep verlaten
      </Button>
      {confirming && (
        <ConfirmDialog
          title={`${group.name} verlaten?`}
          text="Je ziet de groep daarna niet meer. Met de uitnodigingscode kun je later weer meedoen."
          confirmLabel="Groep verlaten"
          busy={leave.isPending}
          error={leave.error ? errorMessage(leave.error) : null}
          onConfirm={() => leave.mutate()}
          onClose={() => setConfirming(false)}
        />
      )}
    </>
  );
}
