# ADR 0004. 브라우저가 Spring API를 직접 호출하고, 프런트와 API는 같은 사이트에 둔다

- 상태: 확정
- 날짜: 2026-11-23

## 배경
- 웹(Next.js) 하나와 API(Spring) 하나가 있다. 로그인, 토큰 재발급, 요청 횟수 제한은 모두 Spring이 담당한다.
- Refresh Token은 `HttpOnly; Secure; SameSite=Strict; Path=/api/auth` 쿠키로 주고받는다. ([ADR 0003](0003-email-verification.md) 이후의 인증 설계)
- 프런트가 API를 부르는 방식에 따라 CORS, 쿠키 전달, 실제 IP 확인 방법이 달라진다.

## 선택지
1. **브라우저 → Spring API 직접 호출** (CORS 허용)
2. 브라우저 → Next.js(rewrites/BFF) → Spring API
3. 브라우저 → 같은 주소의 리버스 프록시(`doodok.xyz/api/*` → Spring)

## 결정
1번. 운영에서는 프런트 `https://doodok.xyz`, API `https://api.doodok.xyz`로 배포한다.

## 이유
- 두 주소는 출처(origin)는 다르지만 등록 도메인이 같아 **같은 사이트(site)**다. 그래서 `SameSite=Strict` 쿠키를 완화하지 않고 CORS 설정만으로 연동할 수 있다.
- 구조가 단순하다. 인증·요청 제한이 Spring 한 곳에 있고, Next.js를 거치지 않아 장애·지연을 진단할 지점이 적다.
- 이후 모바일 등 다른 클라이언트가 생겨도 같은 API를 그대로 쓸 수 있다.
- 2번은 Spring이 보는 연결 주소가 Next.js 서버가 되어, IP별 요청 제한을 위해 실제 IP 전달을 추가로 검증해야 한다. 웹 전용 화면 조합이 필요해질 때 BFF로 도입을 검토한다.

## 구현 규칙
- CORS는 `CORS_ALLOWED_ORIGINS`의 **정확한 출처 목록**만 허용한다. 요청 출처를 그대로 되돌리거나 와일드카드를 쓰지 않는다. (`allowCredentials`와 함께 쓰므로)
- 쿠키가 필요한 인증 요청은 `credentials: 'include'`로 보낸다.
- Access Token은 브라우저 메모리에만 둔다. 그래서 Next.js 서버 렌더링(SSR)에서는 로그인 사용자를 알 수 없다. SEO가 필요한 공개 페이지(매물 목록·상세)는 서버에서 렌더링하고, 내 서재·채팅처럼 로그인이 필요한 화면은 브라우저에서 데이터를 불러온다.

## 감수하는 점
- **프런트와 API를 같은 사이트에 둬야 한다.** 호스팅 기본 주소(`*.vercel.app` 등)에 프런트를 두면 다른 사이트가 되어 Refresh 쿠키가 전송되지 않는다. 그런 구성이 필요해지면 쿠키 정책을 완화하지 않고 3번(리버스 프록시)으로 전환한다.
- `Authorization` 헤더가 붙은 다른 출처 요청에는 브라우저가 사전 요청(`OPTIONS`)을 보낸다.
- 로컬에서 `localhost`와 `127.0.0.1`을 섞으면 다른 사이트가 되어 쿠키가 전달되지 않는다. 둘 다 `localhost`를 쓴다.
