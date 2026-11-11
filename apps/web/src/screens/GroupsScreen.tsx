import { GROUP_COLORS, SPORTS, SPORT_LABELS, seasonOf, type GroupColor, type Sport } from "@split/core";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { Balance, Button, Dialog, Empty, ErrorBanner, Field, GroupName, Leader, SkeletonRows } from "../components/ui";
import { api, errorMessage } from "../lib/api";
import { money, myBalanceSentence, signedMoney, teamLine, teamMembers } from "../lib/format";
import { applyGroup, useGroups } from "../lib/queries";

export function GroupsScreen() {
  const groups = useGroups();
  const [creating, setCreating] = useState(false);
  const list = groups.data ?? [];

  const toReceive = list.reduce((sum, g) => sum + Math.max(0, g.myBalanceCents), 0);
  const toPay = list.reduce((sum, g) => sum + Math.max(0, -g.myBalanceCents), 0);
  const total = toReceive - toPay;

  return (
    <>
      <header className="page-header">
        <div className="page-header__title">
          <h1>Teams</h1>
        </div>
        <div className="page-header__actions">
          <Button variant="primary" onClick={() => setCreating(true)}>
            <Plus size={18} /> Nieuw team
          </Button>
        </div>
      </header>

      <div className="overview">
        <section className="card overview__total" aria-label="Jouw saldo">
          <div className="card__body">
            <p className="muted small">Jouw saldo</p>
            <p className="figure">{signedMoney(total)}</p>
            <p className="small" style={{ marginTop: 2 }}>
              {myBalanceSentence(total)}
            </p>
            <div className="total__split">
              <div className="line">
                <span>Je krijgt nog</span>
                <Leader />
                <span className="line__amount">{money(toReceive)}</span>
              </div>
              <div className="line">
                <span>Je betaalt nog</span>
                <Leader />
                <span className="line__amount">{money(toPay)}</span>
              </div>
            </div>
          </div>
        </section>

        <section className="overview__list" aria-label="Je teams">
          {groups.isPending && (
            <div className="card">
              <SkeletonRows />
            </div>
          )}
          {groups.isError && (
            <div className="card">
              <div className="card__body">
                <ErrorBanner message={errorMessage(groups.error)} />
              </div>
            </div>
          )}
          {groups.isSuccess && list.length === 0 && (
            <div className="card">
              <Empty title="Nog geen teams" text="Maak er een voor je elftal, zevental of trainingsgroep.">
                <Button variant="primary" onClick={() => setCreating(true)}>
                  Nieuw team
                </Button>
              </Empty>
            </div>
          )}
          {list.map((group) => (
            <Link key={group.id} to={`/groups/${group.id}`} className="card group-receipt">
              <span className="group-receipt__name">
                <GroupName name={group.name} color={group.color} />
              </span>
              <span className="group-receipt__sport">{teamLine(group)}</span>
              <span className="line" style={{ marginTop: 6 }}>
                <span className="muted">{teamMembers(group.memberCount)}</span>
                <Leader />
                <span className="line__amount">
                  <Balance cents={group.myBalanceCents} euro />
                </span>
              </span>
            </Link>
          ))}
        </section>

        <JoinCard />
      </div>

      {creating && <NewGroupDialog onClose={() => setCreating(false)} />}
    </>
  );
}

function JoinCard() {
  const navigate = useNavigate();
  const [code, setCode] = useState("");
  return (
    <section className="card overview__join" aria-label="Deelnemen met code">
      <div className="card__body">
        <h2>Deelnemen met code</h2>
        <p className="muted small" style={{ margin: "6px 0 10px" }}>
          Heb je een uitnodigingscode gekregen? Vul hem hier in.
        </p>
        <form
          className="inline-form"
          onSubmit={(event) => {
            event.preventDefault();
            if (code.trim()) navigate(`/join/${encodeURIComponent(code.trim().toUpperCase())}`);
          }}
        >
          <input
            className="input code-input"
            value={code}
            onChange={(event) => setCode(event.target.value)}
            placeholder="Code van 8 tekens"
            aria-label="Uitnodigingscode"
            maxLength={8}
            autoCapitalize="characters"
            autoComplete="off"
            spellCheck={false}
          />
          <Button type="submit" disabled={code.trim().length < 8}>
            Deelnemen
          </Button>
        </form>
      </div>
    </section>
  );
}

function NewGroupDialog({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [sport, setSport] = useState<Sport>("voetbal");
  const [club, setClub] = useState("");
  const [season, setSeason] = useState(() => seasonOf(new Date()));
  const [color, setColor] = useState<GroupColor | undefined>();

  const create = useMutation({
    mutationFn: () => api.createGroup({ name, color, sport, club, season }),
    onSuccess: (group) => {
      applyGroup(queryClient, group);
      navigate(`/groups/${group.id}`);
    },
  });

  return (
    <Dialog
      title="Nieuw team"
      narrow
      onClose={onClose}
      onSubmit={() => create.mutate()}
      footer={
        <>
          <span className="spacer" />
          <Button onClick={onClose}>Annuleren</Button>
          <Button variant="primary" type="submit" disabled={create.isPending || name.trim() === ""}>
            Team maken
          </Button>
        </>
      }
    >
      <Field label="Naam" hint="Bijvoorbeeld: Heren 3, Dames 1 of JO13-2.">
        <input className="input" value={name} onChange={(event) => setName(event.target.value)} maxLength={60} autoFocus />
      </Field>
      <TeamFields sport={sport} club={club} season={season} onSport={setSport} onClub={setClub} onSeason={setSeason} />
      <div className="field">
        <span className="field__label">Markeerstift</span>
        <ColorPicker value={color} onChange={setColor} />
      </div>
      {create.error && (
        <div style={{ marginTop: 16 }}>
          <ErrorBanner message={errorMessage(create.error)} />
        </div>
      )}
    </Dialog>
  );
}

const COLOR_NAMES: Record<GroupColor, string> = {
  amber: "Geel",
  rose: "Roze",
  green: "Mint",
  coral: "Oranje",
  blue: "Blauw",
  lime: "Limoen",
  teal: "Turquoise",
  slate: "Grijs",
};

/** Sport, club and season of a team; shared by the new-team dialog and the team settings. */
export function TeamFields(props: {
  sport: Sport;
  club: string;
  season: string;
  onSport: (sport: Sport) => void;
  onClub: (club: string) => void;
  onSeason: (season: string) => void;
}) {
  return (
    <>
      <Field label="Sport">
        <select className="select" value={props.sport} onChange={(event) => props.onSport(event.target.value as Sport)}>
          {SPORTS.map((sport) => (
            <option key={sport} value={sport}>
              {SPORT_LABELS[sport]}
            </option>
          ))}
        </select>
      </Field>
      <div className="field-row" style={{ marginTop: 18 }}>
        <Field label="Vereniging">
          <input
            className="input"
            value={props.club}
            onChange={(event) => props.onClub(event.target.value)}
            placeholder="Optioneel"
            maxLength={60}
          />
        </Field>
        <Field label="Seizoen">
          <input
            className="input"
            value={props.season}
            onChange={(event) => props.onSeason(event.target.value)}
            placeholder="2026/27"
            maxLength={20}
          />
        </Field>
      </div>
    </>
  );
}

/** Pick one of the eight highlighters for a team. */
export function ColorPicker({
  value,
  onChange,
}: {
  value: GroupColor | undefined;
  onChange: (color: GroupColor) => void;
}) {
  return (
    <div className="swatches" role="group" aria-label="Markeerstift">
      {GROUP_COLORS.map((color) => (
        <button
          key={color}
          type="button"
          className="swatch"
          data-hl={color}
          aria-pressed={value === color}
          aria-label={COLOR_NAMES[color]}
          title={COLOR_NAMES[color]}
          onClick={() => onChange(color)}
        />
      ))}
    </div>
  );
}
