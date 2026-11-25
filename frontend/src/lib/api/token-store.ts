type Listener = (token: string | null) => void;

let accessToken: string | null = null;
const listeners = new Set<Listener>();

/**
 * Access Token은 메모리에만 둔다. localStorage에 두면 XSS 한 번에 토큰이 새어 나간다.
 * 새로고침하면 사라지므로, 앱을 열 때 Refresh 쿠키로 다시 받는다. (AuthProvider)
 */
export const tokenStore = {
  get(): string | null {
    return accessToken;
  },

  set(token: string | null): void {
    if (token === accessToken) return;
    accessToken = token;
    listeners.forEach((listener) => listener(token));
  },

  /** 토큰이 바뀔 때 알림을 받는다. 반환한 함수를 부르면 구독을 끊는다. */
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
};
