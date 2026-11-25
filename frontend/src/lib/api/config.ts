/**
 * API 서버 주소. 브라우저가 이 주소로 직접 요청한다. (docs/adr/0004-direct-api-calls.md)
 * Next.js는 process.env.NEXT_PUBLIC_* 를 그대로 쓴 곳만 빌드 시점에 값으로 바꾸므로, 구조 분해 없이 읽는다.
 */
export function apiBaseUrl(): string {
  const url = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!url) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL이 설정되지 않았습니다. frontend/.env.local을 확인하세요.");
  }
  return url.replace(/\/+$/, "");
}
