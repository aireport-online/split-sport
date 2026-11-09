import { GROUP_COLORS, type GroupColor } from "@split/core";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { Balance, Button, Dialog, Empty, ErrorBanner, Field, SkeletonRows, Tile } from "../components/ui";
import { api, errorMessage } from "../lib/api";
import { money, myBalanceSentence } from "../lib/format";
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
          <h1>Groepen</h1>
        </div>
        <div className="page-header__actions">
          <Button variant="primary" onClick={() => setCreating(true)}>
            <Plus size={16} /> Nieuwe groep
          </Button>
        </div>
      </header>

      <div className="overview">
        <section className="card overview__total" aria-label="Jouw saldo">
          <div className="card__body">
            <p className="total__label">Jouw saldo over alle groepen</p>
            <p className="figure" style={{ color: total > 0 ? "var(--positive)" : undefined }}>
              {myBalanceSentence(total)}
            </p>
            <div className="total__split">
              <div>
                <span className="muted">Je krijgt nog</span>
                <span className="amount">{money(toReceive)}</span>
              </div>
              <div>
                <span className="muted">Je betaalt nog</span>
                <span className="amount">{money(toPay)}</span>
              </div>
            </div>
          </div>
        </section>

        <section className="card overview__list" aria-label="Je groepen">
          {groups.isPending && <SkeletonRows />}
          {groups.isError && (
            <div className="card__body">
              <ErrorBanner message={errorMessage(groups.error)} />
            </div>
          )}
          {groups.isSuccess && list.length === 0 && (
            <Empty title="Nog geen groepen" text="Maak er een voor je huis, vakantie of etentje.">
              <Button variant="primary" onClick={() => setCreating(true)}>
                Nieuwe groep
              </Button>
            </Empty>
          )}
          {list.length > 0 && (
            <ul className="rows" style={{ paddingTop: 8 }}>
              {list.map((group) => (
                <li key={group.id}>
                  <Link to={`/groups/${group.id}`} className="row">
                    <Tile name={group.name} color={group.color} />
                    <span className="row__main">
                      <span className="row__title">{group.name}</span>
                      <span className="row__sub">
                        {group.memberCount} {group.memberCount === 1 ? "lid" : "leden"}
                      </span>
                    </span>
                    <span className="row__end">
                      <Balance cents={group.myBalanceCents} currency={group.currency} />
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
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
        <h2 style={{ fontSize: 16 }}>Deelnemen met code</h2>
        <p className="muted small" style={{ margin: "4px 0 14px" }}>
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
  const [color, setColor] = useState<GroupColor | undefined>();

  const create = useMutation({
    mutationFn: () => api.createGroup({ name, color }),
    onSuccess: (group) => {
      applyGroup(queryClient, group);
      navigate(`/groups/${group.id}`);
    },
  });

  return (
    <Dialog
      title="Nieuwe groep"
      narrow
      onClose={onClose}
      onSubmit={() => create.mutate()}
      footer={
        <>
          <span className="spacer" />
          <Button onClick={onClose}>Annuleren</Button>
          <Button variant="primary" type="submit" disabled={create.isPending || name.trim() === ""}>
            Groep maken
          </Button>
        </>
      }
    >
      <Field label="Naam" hint="Bijvoorbeeld: Huis, Weekend Texel of Vrijdagborrel.">
        <input className="input" value={name} onChange={(event) => setName(event.target.value)} maxLength={60} autoFocus />
      </Field>
      <div className="field">
        <span className="field__label">Kleur</span>
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
  blue: "Blauw",
  amber: "Amber",
  teal: "Zeegroen",
  rose: "Roze",
  green: "Groen",
  coral: "Koraal",
  lime: "Limoen",
  slate: "Grijs",
};

export function ColorPicker({
  value,
  onChange,
}: {
  value: GroupColor | undefined;
  onChange: (color: GroupColor) => void;
}) {
  return (
    <div className="swatches" role="group" aria-label="Kleur">
      {GROUP_COLORS.map((color) => (
        <button
          key={color}
          type="button"
          className="swatch"
          data-tint={color}
          aria-pressed={value === color}
          aria-label={COLOR_NAMES[color]}
          title={COLOR_NAMES[color]}
          onClick={() => onChange(color)}
        />
      ))}
    </div>
  );
}
