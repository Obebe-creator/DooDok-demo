import { apiBaseUrl } from "./config";
import { ApiError, NETWORK_ERROR, UNKNOWN_ERROR } from "./errors";

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export interface SendOptions {
  method?: HttpMethod;
  body?: unknown;
  /** Authorization 헤더에 실을 Access Token */
  token?: string | null;
}

/**
 * fetch 한 번. 응답 상태는 해석하지 않는다.
 * credentials: "include"는 다른 출처(api.doodok.xyz)로 Refresh 쿠키를 주고받는 데 필요하다.
 */
export async function send(path: string, { method = "GET", body, token }: SendOptions = {}): Promise<Response> {
  const headers = new Headers();
  if (body !== undefined) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  try {
    return await fetch(`${apiBaseUrl()}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: "include",
      cache: "no-store",
    });
  } catch (cause) {
    throw new ApiError(0, NETWORK_ERROR, "서버에 연결하지 못했습니다. 네트워크를 확인해 주세요.", [], { cause });
  }
}

/** 2xx면 본문을 JSON으로 읽고(본문이 없으면 undefined), 아니면 ApiError를 던진다. */
export async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) throw await ApiError.from(response);
  try {
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  } catch (cause) {
    throw new ApiError(response.status, UNKNOWN_ERROR, "서버 응답을 읽을 수 없습니다.", [], { cause });
  }
}
