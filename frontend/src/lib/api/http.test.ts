import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authApi } from "./auth";
import { ApiError, NETWORK_ERROR, UNKNOWN_ERROR } from "./errors";
import { request } from "./http";
import { tokenStore } from "./token-store";

const BASE = "http://api.test";

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const tokenExpired = () => json(401, { code: "TOKEN_EXPIRED", message: "토큰이 만료되었습니다.", errors: [] });
const refreshed = (accessToken: string) =>
  json(200, { accessToken, expiresIn: 1800, user: { id: 1, nickname: "두독" } });

/** 요청 URL마다 응답을 정하는 fetch 대역. 보낸 요청은 calls에 쌓인다. */
function mockFetch(handler: (path: string, init: RequestInit) => Response | Promise<Response>) {
  const fetchMock = vi.fn((input: string | URL | Request, init: RequestInit = {}) =>
    Promise.resolve(handler(String(input).replace(BASE, ""), init)),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

const authorization = (init: RequestInit | undefined) => new Headers(init?.headers).get("Authorization");
const pathsOf = (fetchMock: ReturnType<typeof mockFetch>) => fetchMock.mock.calls.map(([input]) => String(input).replace(BASE, ""));

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", `${BASE}/`);
  tokenStore.set(null);
});

afterEach(() => {
  tokenStore.set(null);
});

describe("request", () => {
  it("인증 요청에 Access Token과 쿠키 전송 옵션을 싣는다", async () => {
    tokenStore.set("access-1");
    const fetchMock = mockFetch(() => json(200, { id: 1 }));

    await request("/api/users/me", { auth: true });

    const [input, init] = fetchMock.mock.calls[0];
    expect(input).toBe(`${BASE}/api/users/me`);
    expect(authorization(init)).toBe("Bearer access-1");
    expect(init?.credentials).toBe("include");
  });

  it("본문이 있으면 JSON으로 보낸다", async () => {
    const fetchMock = mockFetch(() => json(202, { expiresIn: 300, resendAvailableIn: 60 }));

    const result = await authApi.sendVerificationCode("reader@doodok.xyz");

    const init = fetchMock.mock.calls[0][1];
    expect(init?.method).toBe("POST");
    expect(new Headers(init?.headers).get("Content-Type")).toBe("application/json");
    expect(init?.body).toBe(JSON.stringify({ email: "reader@doodok.xyz" }));
    expect(result).toEqual({ expiresIn: 300, resendAvailableIn: 60 });
  });

  it("공개 요청에는 토큰이 있어도 Authorization을 싣지 않는다", async () => {
    tokenStore.set("access-1");
    const fetchMock = mockFetch(() => json(200, { signupToken: "t", expiresIn: 600 }));

    await authApi.confirmVerificationCode("reader@doodok.xyz", "123456");

    expect(authorization(fetchMock.mock.calls[0][1])).toBeNull();
  });

  it("본문 없는 응답(204)은 undefined를 돌려준다", async () => {
    mockFetch(() => new Response(null, { status: 204 }));

    await expect(request("/api/auth/logout", { method: "POST" })).resolves.toBeUndefined();
  });

  it("공통 에러 형식을 ApiError로 바꾼다", async () => {
    mockFetch(() =>
      json(400, {
        code: "INVALID_INPUT",
        message: "입력값이 올바르지 않습니다.",
        errors: [{ field: "nickname", reason: "2자 이상이어야 합니다." }],
      }),
    );

    const error = await request("/api/auth/signup", { method: "POST", body: {} }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: "INVALID_INPUT",
      message: "입력값이 올바르지 않습니다.",
      fieldErrors: [{ field: "nickname", reason: "2자 이상이어야 합니다." }],
    });
  });

  it("에러 본문이 공통 형식이 아니면 UNKNOWN_ERROR", async () => {
    mockFetch(() => new Response("<html>Bad Gateway</html>", { status: 502 }));

    await expect(request("/api/users/me")).rejects.toMatchObject({ status: 502, code: UNKNOWN_ERROR });
  });

  it("서버에 닿지 못하면 NETWORK_ERROR", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))));

    await expect(request("/api/users/me")).rejects.toMatchObject({ status: 0, code: NETWORK_ERROR });
  });
});

describe("Access Token 만료", () => {
  it("TOKEN_EXPIRED면 재발급한 토큰으로 한 번 다시 보낸다", async () => {
    tokenStore.set("expired");
    const fetchMock = mockFetch((path, init) => {
      if (path === "/api/auth/refresh") return refreshed("access-2");
      return authorization(init) === "Bearer access-2" ? json(200, { id: 1 }) : tokenExpired();
    });

    await expect(request("/api/users/me", { auth: true })).resolves.toEqual({ id: 1 });

    expect(pathsOf(fetchMock)).toEqual(["/api/users/me", "/api/auth/refresh", "/api/users/me"]);
    expect(tokenStore.get()).toBe("access-2");
  });

  it("동시에 만료된 요청들은 재발급을 한 번만 한다", async () => {
    tokenStore.set("expired");
    const fetchMock = mockFetch((path, init) => {
      if (path === "/api/auth/refresh") return refreshed("access-2");
      return authorization(init) === "Bearer access-2" ? json(200, { path }) : tokenExpired();
    });

    await Promise.all([
      request("/api/users/me", { auth: true }),
      request("/api/books", { auth: true }),
      request("/api/listings", { auth: true }),
    ]);

    expect(pathsOf(fetchMock).filter((path) => path === "/api/auth/refresh")).toHaveLength(1);
  });

  it("다른 요청이 이미 재발급했으면 재발급 없이 새 토큰으로 다시 보낸다", async () => {
    tokenStore.set("expired");
    const fetchMock = mockFetch((path, init) => {
      if (path === "/api/auth/refresh") return refreshed("unexpected");
      if (authorization(init) === "Bearer expired") {
        tokenStore.set("access-2"); // 이 요청이 401을 받기 전에 다른 요청이 재발급을 마친 상황
        return tokenExpired();
      }
      return json(200, { id: 1 });
    });

    await request("/api/users/me", { auth: true });

    expect(pathsOf(fetchMock)).toEqual(["/api/users/me", "/api/users/me"]);
    expect(authorization(fetchMock.mock.calls[1][1])).toBe("Bearer access-2");
  });

  it("재발급이 401이면 토큰을 지우고 원래 에러를 던진다", async () => {
    tokenStore.set("expired");
    mockFetch((path) =>
      path === "/api/auth/refresh"
        ? json(401, { code: "INVALID_REFRESH_TOKEN", message: "다시 로그인해 주세요.", errors: [] })
        : tokenExpired(),
    );

    await expect(request("/api/users/me", { auth: true })).rejects.toMatchObject({ status: 401, code: "TOKEN_EXPIRED" });
    expect(tokenStore.get()).toBeNull();
  });

  it("다시 보낸 요청도 실패하면 재발급을 반복하지 않는다", async () => {
    tokenStore.set("expired");
    const fetchMock = mockFetch((path) => (path === "/api/auth/refresh" ? refreshed("access-2") : tokenExpired()));

    await expect(request("/api/users/me", { auth: true })).rejects.toMatchObject({ code: "TOKEN_EXPIRED" });
    expect(pathsOf(fetchMock)).toEqual(["/api/users/me", "/api/auth/refresh", "/api/users/me"]);
  });

  it("TOKEN_EXPIRED가 아닌 401은 재발급하지 않는다", async () => {
    tokenStore.set("broken");
    const fetchMock = mockFetch(() => json(401, { code: "UNAUTHORIZED", message: "인증이 필요합니다.", errors: [] }));

    await expect(request("/api/users/me", { auth: true })).rejects.toMatchObject({ code: "UNAUTHORIZED" });
    expect(pathsOf(fetchMock)).toEqual(["/api/users/me"]);
  });

  it("브라우저가 Web Locks를 지원하면 잠금 안에서 재발급한다", async () => {
    const lockNames: string[] = [];
    vi.stubGlobal("navigator", {
      locks: {
        request: (name: string, task: () => Promise<unknown>) => {
          lockNames.push(name);
          return task();
        },
      },
    });
    mockFetch(() => refreshed("access-2"));

    await expect(authApi.restoreSession()).resolves.toBe("access-2");
    expect(lockNames).toEqual(["doodok:refresh-token"]);
  });
});

describe("authApi", () => {
  it("로그인하면 Access Token을 저장한다", async () => {
    mockFetch(() => refreshed("access-1"));

    await authApi.login("reader@doodok.xyz", "password1!");

    expect(tokenStore.get()).toBe("access-1");
  });

  it("로그아웃 요청이 실패해도 토큰을 지운다", async () => {
    tokenStore.set("access-1");
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))));

    await expect(authApi.logout()).rejects.toBeInstanceOf(ApiError);
    expect(tokenStore.get()).toBeNull();
  });

  it("세션이 없으면 restoreSession은 null", async () => {
    mockFetch(() => json(401, { code: "INVALID_REFRESH_TOKEN", message: "다시 로그인해 주세요.", errors: [] }));

    await expect(authApi.restoreSession()).resolves.toBeNull();
  });
});
