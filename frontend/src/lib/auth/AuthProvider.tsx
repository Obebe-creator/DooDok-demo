"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { authApi, tokenStore, type CurrentUser } from "@/lib/api";

export type AuthState =
  | { status: "loading" }
  | { status: "authenticated"; user: CurrentUser }
  | { status: "unauthenticated" };

interface AuthContextValue {
  state: AuthState;
  login(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
  /** 프로필 수정 뒤처럼 사용자 정보를 다시 읽어야 할 때 */
  reloadUser(): Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * 로그인 상태를 앱 전체에 제공한다.
 * 처음 열릴 때 Refresh 쿠키로 세션을 되살리고, 토큰이 사라지면(재발급 실패, 로그아웃) 로그아웃 상태로 바꾼다.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading" });

  useEffect(() => {
    let active = true;

    async function restore() {
      try {
        const token = await authApi.restoreSession();
        if (!active) return;
        if (!token) {
          setState({ status: "unauthenticated" });
          return;
        }
        const user = await authApi.me();
        if (active) setState({ status: "authenticated", user });
      } catch {
        if (active) setState({ status: "unauthenticated" });
      }
    }

    void restore();
    const unsubscribe = tokenStore.subscribe((token) => {
      if (token === null) setState({ status: "unauthenticated" });
    });
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  const reloadUser = useCallback(async () => {
    const user = await authApi.me();
    setState({ status: "authenticated", user });
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      await authApi.login(email, password);
      await reloadUser();
    },
    [reloadUser],
  );

  const logout = useCallback(async () => {
    await authApi.logout();
    setState({ status: "unauthenticated" });
  }, []);

  const value = useMemo(() => ({ state, login, logout, reloadUser }), [state, login, logout, reloadUser]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth는 AuthProvider 안에서만 쓸 수 있습니다.");
  return context;
}
