import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Button, ConfirmDialog, ErrorBanner, Field } from "../components/ui";
import { api, errorMessage, tokenStore } from "../lib/api";
import { formatIban } from "../lib/format";
import { useInvalidateGroups } from "../lib/queries";
import { useSession, useUser } from "../lib/session";

export function ProfileScreen() {
  const user = useUser();
  const { setUser, signOut } = useSession();
  const invalidate = useInvalidateGroups();
  const [name, setName] = useState(user.name);
  const [iban, setIban] = useState(user.iban ? formatIban(user.iban) : "");
  const [confirming, setConfirming] = useState(false);

  const save = useMutation({
    mutationFn: () => api.updateMe({ name, iban }),
    onSuccess: async (updated) => {
      setUser(updated);
      setIban(updated.iban ? formatIban(updated.iban) : "");
      await invalidate();
    },
  });

  const remove = useMutation({
    mutationFn: api.deleteMe,
    onSuccess: () => {
      tokenStore.clear();
      window.location.assign("/login");
    },
  });

  const changed = name.trim() !== user.name || iban.replace(/\s/g, "").toUpperCase() !== (user.iban ?? "");

  return (
    <>
      <header className="page-header">
        <h1>Profiel</h1>
      </header>

      <section className="settings-section">
        <div>
          <h2 className="settings-section__title">Jouw gegevens</h2>
          <p className="settings-section__text">
            Je naam zien je teamgenoten. Vul je IBAN in zodat teamgenoten weten waar ze kunnen betalen; ben je
            penningmeester, dan gaat de inleg daarheen.
          </p>
        </div>
        <form
          className="panel"
          onSubmit={(event) => {
            event.preventDefault();
            save.mutate();
          }}
        >
          <Field label="Naam">
            <input className="input" value={name} onChange={(event) => setName(event.target.value)} maxLength={60} autoComplete="name" />
          </Field>
          <Field label="E-mailadres">
            <input className="input" value={user.email} readOnly />
          </Field>
          <Field label="IBAN" hint="Optioneel. Alleen zichtbaar voor je teamgenoten.">
            <input
              className="input"
              value={iban}
              onChange={(event) => setIban(event.target.value)}
              placeholder="NL00 BANK 0000 0000 00"
              autoComplete="off"
              spellCheck={false}
            />
          </Field>
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
      </section>

      <section className="settings-section">
        <div>
          <h2 className="settings-section__title">Uitloggen</h2>
          <p className="settings-section__text">Op dit apparaat. Je teams en uitgaven blijven bewaard.</p>
        </div>
        <div className="panel">
          <Button onClick={() => void signOut()}>Uitloggen</Button>
        </div>
      </section>

      <section className="settings-section">
        <div>
          <h2 className="settings-section__title">Account verwijderen</h2>
          <p className="settings-section__text">
            Je account en je inloggegevens worden gewist. In teams waar je uitgaven hebt blijft alleen je naam staan,
            zodat de saldo's van de anderen kloppen.
          </p>
        </div>
        <div className="panel">
          <Button variant="danger" onClick={() => setConfirming(true)}>
            Account verwijderen
          </Button>
        </div>
      </section>

      {confirming && (
        <ConfirmDialog
          title="Account verwijderen?"
          text="Dit kan niet ongedaan worden gemaakt."
          confirmLabel="Account verwijderen"
          busy={remove.isPending}
          error={remove.error ? errorMessage(remove.error) : null}
          onConfirm={() => remove.mutate()}
          onClose={() => setConfirming(false)}
        />
      )}
    </>
  );
}
