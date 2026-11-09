import { useQueryClient } from "@tanstack/react-query";
import type { AuthResponse, User } from "@split/core";
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, setUnauthorizedHandler, tokenStore } from "./api";

interface Session {
  /** Null while signed out. */
  user: User | null;
  /** True until the stored token has been checked. */
  loading: boolean;
  signIn: (auth: AuthResponse) => void;
  signOut: () => Promise<void>;
  setUser: (user: User) => void;
}

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(() => tokenStore.get() !== null);

  const reset = useCallback(() => {
    tokenStore.clear();
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(reset);
    if (!tokenStore.get()) return;
    api
      .me()
      .then(setUser)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [reset]);

  const session = useMemo<Session>(
    () => ({
      user,
      loading,
      setUser,
      signIn: (auth) => {
        tokenStore.set(auth.token);
        queryClient.clear();
        setUser(auth.user);
      },
      signOut: async () => {
        await api.logout().catch(() => {});
        reset();
      },
    }),
    [user, loading, queryClient, reset],
  );

  return <SessionContext.Provider value={session}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error("useSession must be used inside SessionProvider");
  return session;
}

/** The signed-in user; only for screens behind the login. */
export function useUser(): User {
  const { user } = useSession();
  if (!user) throw new Error("useUser needs a signed-in user");
  return user;
}
