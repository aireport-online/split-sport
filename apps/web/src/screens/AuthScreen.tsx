import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Link, Navigate, useLocation } from "react-router";
import { Button, ErrorBanner, Field, Wordmark } from "../components/ui";
import { api, errorMessage } from "../lib/api";
import { useSession } from "../lib/session";

export function AuthScreen({ mode }: { mode: "login" | "register" }) {
  const { user, loading, signIn } = useSession();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/";
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const submit = useMutation({
    mutationFn: () => (mode === "login" ? api.login({ email, password }) : api.register({ name, email, password })),
    onSuccess: signIn,
  });

  if (loading) return null;
  if (user) return <Navigate to={from} replace />;

  const register = mode === "register";

  return (
    <main className="auth">
      <div className="auth__inner">
        <Wordmark size="large" />
        <div className="card auth__card">
        <h1>{register ? "Account maken" : "Inloggen"}</h1>
        <p className="muted small" style={{ marginTop: 4 }}>
          {register ? "Houd met je groep bij wie wat betaald heeft." : "Bekijk je groepen en wat er nog openstaat."}
        </p>
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            submit.mutate();
          }}
        >
          {register && (
            <Field label="Naam">
              <input
                className="input"
                value={name}
                onChange={(event) => setName(event.target.value)}
                autoComplete="name"
                autoFocus
                required
              />
            </Field>
          )}
          <Field label="E-mailadres">
            <input
              className="input"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              autoFocus={!register}
              required
            />
          </Field>
          <Field label="Wachtwoord" hint={register ? "Minstens 8 tekens." : undefined}>
            <input
              className="input"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete={register ? "new-password" : "current-password"}
              required
            />
          </Field>
          <div style={{ marginTop: 16 }}>
            <ErrorBanner message={submit.error ? errorMessage(submit.error) : null} />
          </div>
          <Button variant="primary" type="submit" block disabled={submit.isPending} style={{ marginTop: 20 }}>
            {register ? "Account maken" : "Inloggen"}
          </Button>
        </form>
        </div>
        <p className="auth__switch">
          {register ? "Heb je al een account? " : "Nog geen account? "}
          <Link className="link" to={register ? "/login" : "/register"} state={location.state}>
            {register ? "Inloggen" : "Account maken"}
          </Link>
        </p>
      </div>
    </main>
  );
}
