import { ApiError } from "./errors";
import { refreshAccessToken } from "./refresh";
import { tokenStore } from "./token-store";
import { parse, send, type HttpMethod } from "./transport";

const TOKEN_EXPIRED = "TOKEN_EXPIRED";

export interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  /** true면 Access Token을 싣고, 만료(TOKEN_EXPIRED)되면 한 번 재발급한 뒤 다시 보낸다. */
  auth?: boolean;
}

/** 모든 API 호출의 입구. 실패하면 ApiError를 던진다. */
export async function request<T>(path: string, { method, body, auth = false }: RequestOptions = {}): Promise<T> {
  if (!auth) return parse<T>(await send(path, { method, body }));

  const usedToken = tokenStore.get();
  const response = await send(path, { method, body, token: usedToken });
  if (response.status !== 401) return parse<T>(response);

  const error = await ApiError.from(response);
  if (error.code !== TOKEN_EXPIRED) throw error;

  // 기다리는 사이 다른 요청이 이미 재발급했다면 그 토큰으로 바로 다시 보낸다.
  const current = tokenStore.get();
  const token = current && current !== usedToken ? current : await refreshAccessToken();
  if (!token) throw error;

  // 다시 보낸 요청도 실패하면 그대로 던진다. 재발급을 반복하지 않는다.
  return parse<T>(await send(path, { method, body, token }));
}
