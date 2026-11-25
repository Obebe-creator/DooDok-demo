"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { authApi, tokenStore, type CurrentUser, type TokenResponse } from "@/lib/api";

type ProfileState =
  | { status: "loading" }
  | { status: "ready"; user: CurrentUser }
  | { status: "error"; error: unknown };

export type AuthState =
  | { status: "loading" }
  | { status: "authenticated"; user: TokenResponse["user"]; profile: ProfileState }
  | { status: "restoreError"; error: unknown }
  | { status: "unauthenticated" };

interface AuthContextValue {
  state: AuthState;
  login(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
  retryRestore(): Promise<void>;
  /** 프로필 수정 뒤처럼 사용자 정보를 다시 읽어야 할 때 */
  reloadUser(): Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * 로그인 상태를 앱 전체에 제공한다.
 * 처음 열릴 때 Refresh 쿠키로 세션을 되살린다. 전체 프로필은 별도로 조회한다.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading" });
  const restoreAttempt = useRef(0);
  const profileAttempt = useRef(0);

  const reloadUser = useCallback(async () => {
    if (tokenStore.get() === null) return;
    const generation = tokenStore.generation();
    const attempt = ++profileAttempt.current;
    const isCurrent = () => generation === tokenStore.generation() && attempt === profileAttempt.current;
    const updateProfile = (profile: ProfileState) => {
      setState((current) => {
        if (current.status !== "authenticated") return current;
        const user = profile.status === "ready" ? { id: profile.user.id, nickname: profile.user.nickname } : current.user;
        return { ...current, user, profile };
      });
    };

    updateProfile({ status: "loading" });
    try {
      const user = await authApi.me();
      if (isCurrent()) updateProfile({ status: "ready", user });
    } catch (error) {
      if (isCurrent()) updateProfile({ status: "error", error });
      throw error;
    }
  }, []);

  const establishSession = useCallback((user: TokenResponse["user"]) => {
    setState({ status: "authenticated", user, profile: { status: "loading" } });
    void reloadUser().catch(() => {}); // 조회 실패는 profile.error 상태에 표시한다.
  }, [reloadUser]);

  const retryRestore = useCallback(async () => {
    const generation = tokenStore.generation();
    const attempt = ++restoreAttempt.current;
    const isCurrent = () => generation === tokenStore.generation() && attempt === restoreAttempt.current;
    setState({ status: "loading" });
    try {
      const session = await authApi.restoreSession();
      if (!isCurrent()) return;
      if (session) establishSession(session.user);
      else setState({ status: "unauthenticated" });
    } catch (error) {
      if (isCurrent()) setState({ status: "restoreError", error });
    }
  }, [establishSession]);

  useEffect(() => {
    const unsubscribe = tokenStore.subscribe((token) => {
      if (token === null) setState({ status: "unauthenticated" });
    });
    void retryRestore();
    return () => {
      restoreAttempt.current += 1;
      profileAttempt.current += 1;
      unsubscribe();
    };
  }, [retryRestore]);

  const login = useCallback(
    async (email: string, password: string) => {
      const session = await authApi.login(email, password);
      establishSession(session.user);
    },
    [establishSession],
  );

  const value = useMemo(
    () => ({ state, login, logout: authApi.logout, retryRestore, reloadUser }),
    [state, login, retryRestore, reloadUser],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth는 AuthProvider 안에서만 쓸 수 있습니다.");
  return context;
}
