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

  it.each([["invalid"], [null], [[null]], [[{ field: "nickname", reason: 42 }]]])(
    "잘못된 errors(%j)는 UNKNOWN_ERROR로 변환한다",
    async (errors) => {
      mockFetch(() => json(400, { code: "INVALID_INPUT", message: "invalid", errors }));
      await expect(request("/api/auth/signup")).rejects.toMatchObject({
        status: 400, code: UNKNOWN_ERROR, fieldErrors: [],
      });
    },
  );

  it("errors가 없으면 빈 배열을 반환한다", async () => {
    mockFetch(() => json(400, { code: "INVALID_INPUT", message: "invalid" }));
    await expect(request("/api/auth/signup")).rejects.toMatchObject({ code: "INVALID_INPUT", fieldErrors: [] });
  });

  it("200 응답의 JSON이 잘못되면 HTTP 상태를 보존한 ApiError로 변환한다", async () => {
    mockFetch(() => new Response("<html>Gateway</html>", { status: 200 }));
    await expect(request("/api/users/me")).rejects.toMatchObject({ status: 200, code: UNKNOWN_ERROR });
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

  it("브라우저가 Web Locks를 지원하면 쿠키를 바꾸는 요청들이 같은 잠금을 사용한다", async () => {
    const lockNames: string[] = [];
    vi.stubGlobal("navigator", {
      locks: {
        request: (name: string, task: () => Promise<unknown>) => {
          lockNames.push(name);
          return task();
        },
      },
    });
    mockFetch((path) => path === "/api/auth/logout" ? new Response(null, { status: 204 }) : refreshed("access-2"));

    await authApi.login("reader@doodok.xyz", "password1!");
    await expect(authApi.restoreSession()).resolves.toMatchObject({ accessToken: "access-2" });
    await authApi.logout();
    expect(lockNames).toEqual(Array(3).fill("doodok:refresh-token"));
  });
});

describe("authApi", () => {
  it("로그인하면 Access Token을 저장한다", async () => {
    mockFetch(() => refreshed("access-1"));

    await authApi.login("reader@doodok.xyz", "password1!");

    expect(tokenStore.get()).toBe("access-1");
  });

  it("로그아웃 요청이 실패하면 기존 토큰을 유지한다", async () => {
    tokenStore.set("access-1");
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))));

    await expect(authApi.logout()).rejects.toBeInstanceOf(ApiError);
    expect(tokenStore.get()).toBe("access-1");
  });

  it("로그아웃이 성공하면 토큰을 지운다", async () => {
    tokenStore.set("access-1");
    mockFetch(() => new Response(null, { status: 204 }));

    await authApi.logout();
    expect(tokenStore.get()).toBeNull();
  });

  it("세션이 없으면 restoreSession은 null", async () => {
    mockFetch(() => json(401, { code: "INVALID_REFRESH_TOKEN", message: "다시 로그인해 주세요.", errors: [] }));

    await expect(authApi.restoreSession()).resolves.toBeNull();
  });

  it("형식이 다른 재발급 401은 세션 부재로 처리하지 않는다", async () => {
    tokenStore.set("access-1");
    mockFetch(() => new Response("Unauthorized", { status: 401 }));

    await expect(authApi.restoreSession()).rejects.toMatchObject({ status: 401, code: UNKNOWN_ERROR });
    expect(tokenStore.get()).toBe("access-1");
  });
});

describe("세션 전환", () => {
  it("다른 탭에서 계정이 바뀌었으면 재발급 결과로 기존 요청을 재시도하지 않는다", async () => {
    tokenStore.replaceSession("account-a", 1);
    const fetchMock = mockFetch((path) => path === "/api/auth/refresh"
      ? json(200, { accessToken: "account-b", expiresIn: 1800, user: { id: 2, nickname: "다른 독자" } })
      : tokenExpired());

    await expect(request("/api/listings", { method: "POST", auth: true })).rejects.toMatchObject({ code: "TOKEN_EXPIRED" });
    expect(pathsOf(fetchMock)).toEqual(["/api/listings", "/api/auth/refresh"]);
    expect(tokenStore.get()).toBeNull();
  });

  it("계정 전환 뒤 이전 계정의 만료된 쓰기 요청을 재시도하지 않는다", async () => {
    tokenStore.replaceSession("account-a");
    const write = Promise.withResolvers<Response>();
    const fetchMock = mockFetch((path) => {
      if (path === "/api/auth/login") return refreshed("account-b");
      if (path === "/api/auth/logout") return new Response(null, { status: 204 });
      return write.promise;
    });

    const oldRequest = request("/api/listings", { method: "POST", auth: true, body: { price: 1000 } });
    const rejection = expect(oldRequest).rejects.toMatchObject({ code: "TOKEN_EXPIRED" });
    await authApi.logout();
    await authApi.login("second@doodok.xyz", "password1!");
    write.resolve(tokenExpired());
    await rejection;

    expect(pathsOf(fetchMock)).toEqual(["/api/listings", "/api/auth/logout", "/api/auth/login"]);
    expect(tokenStore.get()).toBe("account-b");
  });

  it("재발급이 쿠키를 교체한 뒤 로그아웃하고 이후 복원해도 세션이 없다", async () => {
    vi.stubGlobal("navigator", {});
    tokenStore.replaceSession("access-1");
    const refreshStarted = Promise.withResolvers<void>();
    const finishRefresh = Promise.withResolvers<void>();
    let cookie: string | null = "cookie-1";
    const fetchMock = mockFetch(async (path) => {
      if (path === "/api/auth/logout") {
        expect(cookie).toBe("cookie-2");
        cookie = null;
        return new Response(null, { status: 204 });
      }
      if (cookie === null) return json(401, { code: "INVALID_REFRESH_TOKEN", message: "세션 없음" });
      refreshStarted.resolve();
      await finishRefresh.promise;
      cookie = "cookie-2";
      return refreshed("access-2");
    });

    const refresh = authApi.restoreSession();
    await refreshStarted.promise;
    const logout = authApi.logout();
    await Promise.resolve();
    expect(pathsOf(fetchMock)).toEqual(["/api/auth/refresh"]);
    finishRefresh.resolve();
    await Promise.all([refresh, logout]);

    expect(tokenStore.get()).toBeNull();
    await expect(authApi.restoreSession()).resolves.toBeNull();
    expect(pathsOf(fetchMock)).toEqual(["/api/auth/refresh", "/api/auth/logout", "/api/auth/refresh"]);
  });

  it("로그아웃이 진행되는 동안 예약된 이전 세션 재발급을 건너뛴다", async () => {
    tokenStore.replaceSession("access-1");
    const logoutStarted = Promise.withResolvers<void>();
    const finishLogout = Promise.withResolvers<Response>();
    const fetchMock = mockFetch(() => {
      logoutStarted.resolve();
      return finishLogout.promise;
    });

    const logout = authApi.logout();
    await logoutStarted.promise;
    const refresh = authApi.restoreSession();
    finishLogout.resolve(new Response(null, { status: 204 }));
    await logout;
    await expect(refresh).resolves.toBeNull();

    expect(pathsOf(fetchMock)).toEqual(["/api/auth/logout"]);
    expect(tokenStore.get()).toBeNull();
  });

  it.each([200, 401])("늦은 재발급 %i가 새 세션의 토큰을 바꾸지 않는다", async (status) => {
    tokenStore.replaceSession("account-a");
    const started = Promise.withResolvers<void>();
    const response = Promise.withResolvers<Response>();
    const fetchMock = mockFetch(() => {
      started.resolve();
      return response.promise;
    });

    const previous = authApi.restoreSession();
    await started.promise;
    tokenStore.replaceSession("account-b");
    fetchMock.mockImplementationOnce(async () => refreshed("account-b-refreshed"));
    const current = authApi.restoreSession();
    expect(current).not.toBe(previous);
    response.resolve(status === 200
      ? refreshed("stale")
      : json(401, { code: "INVALID_REFRESH_TOKEN", message: "세션 없음" }));

    await expect(previous).resolves.toBeNull();
    await expect(current).resolves.toMatchObject({ accessToken: "account-b-refreshed" });
    expect(tokenStore.get()).toBe("account-b-refreshed");
  });
});
