import { ApiError } from "./errors";
import { withSessionLock } from "./session-lock";
import { tokenStore } from "./token-store";
import { parse, send } from "./transport";
import type { TokenResponse } from "./types";

let inflight: { generation: number; result: Promise<TokenResponse | null> } | undefined;

/** 같은 세션의 호출은 진행 중인 재발급을 공유한다. */
export function refreshAccessToken(): Promise<TokenResponse | null> {
  const generation = tokenStore.generation();
  if (inflight?.generation === generation) return inflight.result;

  const result = withSessionLock(() => requestRefresh(generation)).finally(() => {
    if (inflight?.result === result) inflight = undefined;
  });
  inflight = { generation, result };
  return result;
}

async function requestRefresh(generation: number): Promise<TokenResponse | null> {
  if (generation !== tokenStore.generation()) return null;

  const response = await send("/api/auth/refresh", { method: "POST" });
  if (response.status === 401) {
    const error = await ApiError.from(response);
    if (generation !== tokenStore.generation()) return null;
    if (error.code !== "INVALID_REFRESH_TOKEN") throw error;
    if (tokenStore.get() !== null) tokenStore.replaceSession(null);
    return null;
  }

  const session = await parse<TokenResponse>(response);
  if (generation !== tokenStore.generation()) return null;
  const userId = tokenStore.userId();
  // 다른 탭에서 같은 쿠키로 다른 계정에 로그인했을 수 있다.
  if (userId !== null && userId !== session.user.id) {
    tokenStore.replaceSession(null);
    return null;
  }
  tokenStore.set(session.accessToken, session.user.id);
  return session;
}
