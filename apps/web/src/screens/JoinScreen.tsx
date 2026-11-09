import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { Avatar, Button, Empty, ErrorBanner, SkeletonRows, Tile } from "../components/ui";
import { api, errorMessage } from "../lib/api";
import { applyGroup, keys } from "../lib/queries";
import { useUser } from "../lib/session";

const NEW_MEMBER = "new";

/** Join a group by invite code, optionally taking over a name that was already added. */
export function JoinScreen() {
  const { code = "" } = useParams();
  const user = useUser();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [typed, setTyped] = useState("");
  const [choice, setChoice] = useState<string | null>(null);

  const invite = useQuery({ queryKey: keys.invite(code), queryFn: () => api.invite(code), enabled: code !== "" });
  const join = useMutation({
    mutationFn: () => api.join(code, choice && choice !== NEW_MEMBER ? choice : undefined),
    onSuccess: (group) => {
      applyGroup(queryClient, group);
      navigate(`/groups/${group.id}`, { replace: true });
    },
  });

  if (code === "") {
    return (
      <>
        <header className="page-header">
          <h1>Deelnemen</h1>
        </header>
        <section className="card join-card">
          <div className="card__body">
            <p className="muted" style={{ marginBottom: 16 }}>
              Vul de uitnodigingscode in die je hebt gekregen.
            </p>
            <form
              className="inline-form"
              onSubmit={(event) => {
                event.preventDefault();
                if (typed.trim()) navigate(`/join/${encodeURIComponent(typed.trim().toUpperCase())}`);
              }}
            >
              <input
                className="input code-input"
                value={typed}
                onChange={(event) => setTyped(event.target.value)}
                placeholder="Code van 8 tekens"
                aria-label="Uitnodigingscode"
                maxLength={8}
                autoFocus
              />
              <Button variant="primary" type="submit" disabled={typed.trim().length < 8}>
                Verder
              </Button>
            </form>
          </div>
        </section>
      </>
    );
  }

  return (
    <>
      <header className="page-header">
        <h1>Deelnemen</h1>
      </header>
      <section className="card join-card">
        {invite.isPending && <SkeletonRows count={2} />}
        {invite.isError && (
          <Empty title="Deze uitnodiging werkt niet" text={errorMessage(invite.error)}>
            <Link className="button" to="/join">
              Andere code invullen
            </Link>
          </Empty>
        )}
        {invite.isSuccess && (
          <div className="card__body">
            <div className="page-header__title" style={{ marginBottom: 20 }}>
              <Tile name={invite.data.name} color={invite.data.color} size="large" />
              <div>
                <h2>{invite.data.name}</h2>
                <p className="muted small">
                  {invite.data.memberCount} {invite.data.memberCount === 1 ? "lid" : "leden"}
                </p>
              </div>
            </div>

            {invite.data.alreadyMember ? (
              <>
                <p className="muted">Je doet al mee met deze groep.</p>
                <div className="panel__actions">
                  <Link className="button button--primary" to={`/groups/${invite.data.groupId}`}>
                    Naar de groep
                  </Link>
                </div>
              </>
            ) : (
              <>
                {invite.data.unclaimedMembers.length > 0 && (
                  <fieldset style={{ margin: 0, padding: 0, border: 0 }}>
                    <legend className="field__label">Wie ben jij?</legend>
                    {invite.data.unclaimedMembers.map((member) => (
                      <button
                        key={member.id}
                        type="button"
                        className="choice"
                        aria-pressed={choice === member.id}
                        onClick={() => setChoice(member.id)}
                      >
                        <Avatar name={member.name} color="slate" small />
                        <span>
                          {member.name}
                          <span className="row__sub">Staat al in de groep; neem dit saldo over</span>
                        </span>
                      </button>
                    ))}
                    <button
                      type="button"
                      className="choice"
                      aria-pressed={choice === NEW_MEMBER}
                      onClick={() => setChoice(NEW_MEMBER)}
                    >
                      <Avatar name={user.name} color="blue" small />
                      <span>
                        Ik sta er niet tussen
                        <span className="row__sub">Doe mee als {user.name}</span>
                      </span>
                    </button>
                  </fieldset>
                )}
                {join.error && (
                  <div style={{ marginTop: 16 }}>
                    <ErrorBanner message={errorMessage(join.error)} />
                  </div>
                )}
                <div className="panel__actions">
                  <Button
                    variant="primary"
                    disabled={join.isPending || (invite.data.unclaimedMembers.length > 0 && choice === null)}
                    onClick={() => join.mutate()}
                  >
                    Meedoen met {invite.data.name}
                  </Button>
                </div>
              </>
            )}
          </div>
        )}
      </section>
    </>
  );
}
