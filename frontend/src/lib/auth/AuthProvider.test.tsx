// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { tokenStore } from "@/lib/api";
import { AuthProvider, useAuth } from "./AuthProvider";

const BASE = "http://api.test";
const reader = { id: 1, nickname: "두독" };
const profile = { ...reader, email: "reader@doodok.xyz", profileImageUrl: null, createdAt: "2026-11-25T00:00:00Z" };

const session = () => Response.json({ accessToken: "access-1", expiresIn: 1800, user: reader });
const noSession = () => Response.json({ code: "INVALID_REFRESH_TOKEN", message: "세션 없음" }, { status: 401 });

let root: Root | undefined;
let auth: ReturnType<typeof useAuth>;

function mockFetch(handler: (path: string) => Response | Promise<Response>) {
  vi.stubGlobal("fetch", vi.fn(async (input: string) => handler(new URL(input).pathname)));
}

async function mountProvider() {
  function Consumer() {
    auth = useAuth();
    return null;
  }

  const container = document.createElement("div");
  const mountedRoot = createRoot(container);
  root = mountedRoot;
  await act(async () => {
    mountedRoot.render(<AuthProvider><Consumer /></AuthProvider>);
  });
}

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", BASE);
  vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
  tokenStore.replaceSession(null);
});

afterEach(async () => {
  await act(async () => root?.unmount());
  root = undefined;
});

describe("AuthProvider", () => {
  it("프로필 조회가 실패해도 로그인은 성공하고 프로필만 다시 조회할 수 있다", async () => {
    let profileAvailable = false;
    mockFetch((path) => {
      if (path === "/api/auth/refresh") return noSession();
      if (path === "/api/auth/login") return session();
      if (profileAvailable) return Response.json(profile);
      throw new TypeError("Failed to fetch");
    });
    await mountProvider();

    await act(async () => auth.login(profile.email, "password1!"));
    expect(auth.state).toMatchObject({ status: "authenticated", user: reader, profile: { status: "error" } });

    profileAvailable = true;
    await act(async () => auth.reloadUser());
    expect(auth.state).toEqual({ status: "authenticated", user: reader, profile: { status: "ready", user: profile } });
  });

  it.each(["logout", "switch"] as const)("늦은 /me 응답이 세션 전환(%s) 이후 상태를 바꾸지 않는다", async (action) => {
    const oldProfile = Promise.withResolvers<Response>();
    let loginCount = 0;
    const newReader = { id: 2, nickname: "두 번째 독자" };
    const newProfile = { ...profile, ...newReader, email: "second@doodok.xyz" };
    mockFetch((path) => {
      if (path === "/api/auth/refresh") return noSession();
      if (path === "/api/auth/logout") return new Response(null, { status: 204 });
      if (path === "/api/auth/login") {
        loginCount += 1;
        return loginCount === 1 ? session() : Response.json({ accessToken: "access-2", user: newReader });
      }
      return loginCount === 1 ? oldProfile.promise : Response.json(newProfile);
    });
    await mountProvider();
    await act(async () => auth.login(profile.email, "password1!"));
    expect(auth.state).toMatchObject({ status: "authenticated", profile: { status: "loading" } });

    await act(async () => auth.logout());
    if (action === "switch") await act(async () => auth.login(newProfile.email, "password1!"));
    await act(async () => oldProfile.resolve(Response.json(profile)));

    expect(auth.state).toEqual(action === "logout"
      ? { status: "unauthenticated" }
      : { status: "authenticated", user: newReader, profile: { status: "ready", user: newProfile } });
  });

  it("초기 세션 확인 오류를 표시하고 재시도로 세션을 복원한다", async () => {
    let apiAvailable = false;
    mockFetch((path) => {
      if (!apiAvailable) return new Response(null, { status: 503 });
      return path === "/api/auth/refresh" ? session() : Response.json(profile);
    });
    await mountProvider();
    expect(auth.state).toMatchObject({ status: "restoreError", error: { status: 503 } });

    apiAvailable = true;
    await act(async () => auth.retryRestore());
    expect(auth.state).toEqual({ status: "authenticated", user: reader, profile: { status: "ready", user: profile } });
  });
});
