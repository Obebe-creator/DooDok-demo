import { tokenStore } from "./token-store";
import { parse, send } from "./transport";
import type { TokenResponse } from "./types";

const REFRESH_LOCK = "doodok:refresh-token";

let inflight: Promise<string | null> | null = null;

/**
 * Refresh 쿠키로 Access Token을 다시 받는다. 성공하면 새 토큰, 세션이 없으면 null.
 *
 * 서버는 재발급 때마다 Refresh Token을 교체하고 이전 토큰을 지운다.
 * 같은 쿠키로 두 번 요청하면 뒤의 요청은 401이 되어 로그아웃되므로, 재발급이 겹치지 않게 막는다.
 * - 한 탭 안: 진행 중인 재발급 Promise를 함께 기다린다. (StrictMode의 effect 두 번 실행, 동시에 만료된 여러 요청)
 * - 여러 탭: navigator.locks로 순서를 정한다. 뒤의 탭은 앞의 탭이 바꿔 둔 새 쿠키로 요청한다.
 */
export function refreshAccessToken(): Promise<string | null> {
  inflight ??= withRefreshLock(requestRefresh).finally(() => {
    inflight = null;
  });
  return inflight;
}

async function requestRefresh(): Promise<string | null> {
  const response = await send("/api/auth/refresh", { method: "POST" });
  if (response.status === 401) {
    tokenStore.set(null);
    return null;
  }
  const { accessToken } = await parse<TokenResponse>(response);
  tokenStore.set(accessToken);
  return accessToken;
}

async function withRefreshLock(task: () => Promise<string | null>): Promise<string | null> {
  if (typeof navigator !== "undefined" && navigator.locks) {
    return await navigator.locks.request(REFRESH_LOCK, task);
  }
  return task();
}
