const SESSION_LOCK = "doodok:refresh-token";

let pending = Promise.resolve();

/** Refresh 쿠키를 바꾸는 요청은 같은 탭에서, Web Locks 지원 시 여러 탭에서도 순서대로 실행한다. */
export function withSessionLock<T>(task: () => Promise<T>): Promise<T> {
  const result = pending.then(async () => {
    if (typeof navigator !== "undefined" && navigator.locks) {
      return await navigator.locks.request(SESSION_LOCK, task);
    }
    return task();
  });
  pending = result.then(() => undefined, () => undefined);
  return result;
}
