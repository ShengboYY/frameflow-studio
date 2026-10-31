"use client";

import { createContext, useContext, useEffect, useState, useSyncExternalStore, type ReactNode } from "react";
import { AUTH_CHANGE_KEY, AuthSession } from "./auth-session";

const AuthContext = createContext<AuthSession | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session] = useState(() => new AuthSession());
  useEffect(() => {
    const recheck = () => { void session.recheck(); };
    const changed = (event: StorageEvent) => {
      if (event.key === AUTH_CHANGE_KEY || event.key === null) void session.refresh(true);
    };
    session.start();
    window.addEventListener("focus", recheck);
    window.addEventListener("storage", changed);
    return () => {
      session.stop();
      window.removeEventListener("focus", recheck);
      window.removeEventListener("storage", changed);
    };
  }, [session]);
  return <AuthContext.Provider value={session}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const session = useContext(AuthContext);
  if (!session) throw new Error("useAuth requires AuthProvider");
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getServerSnapshot);
  return { ...state, session };
}
