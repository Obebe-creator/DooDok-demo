# 기술 스택과 선택 이유

이번 프로젝트의 목표는 기존 이력에서 비어 있는 역량(Java/Spring, 테스트, CI/CD, 캐시, LLM 활용)을 채우는 것이다.
이미 증명된 영역(React/Next.js)은 유지하고, 새로 배우는 비용은 백엔드와 인프라에 집중한다.

| 영역 | 기술 | 이유 |
|---|---|---|
| 프런트 | Next.js (App Router) + TypeScript + Tailwind | 기존 강점. 매물 페이지 SEO를 위해 SSR 필요 |
| 백엔드 | Java 21 + Spring Boot 4 | 국내 백엔드 채용에서 가장 범용적인 조합 |
| ORM | Spring Data JPA (+ QueryDSL 예정) | Prisma 외 ORM 경험, 동적 검색 쿼리 |
| DB 마이그레이션 | Flyway | 부분 인덱스·pgvector처럼 JPA가 만들 수 없는 스키마를 SQL로 버전 관리 ([ADR 0002](adr/0002-flyway-testcontainers.md)) |
| 인증 | Spring Security + JWT, 가입 전 이메일 인증 | Spring 생태계 핵심. 이후 구글·카카오 로그인에서 같은 이메일 계정을 안전하게 연결하려면 자체 가입 이메일이 인증돼 있어야 한다 ([ADR 0003](adr/0003-email-verification.md)) |
| DB | PostgreSQL + pgvector | 일반 데이터와 임베딩을 한 DB에서 관리 |
| Redis | Redis + Lua 스크립트 | 지금: 이메일 인증 코드·signupToken(TTL), 요청 횟수 제한(카운터를 Lua로 원자 처리). 이후: 도서 정보·매물 목록 캐시, 인기 순위, 채팅 Pub/Sub |
| 메일 | Spring Mail (SMTP) | 로컬은 Mailpit(실제 발송 없이 웹에서 확인), 운영은 AWS SES. SES도 SMTP를 지원해 설정만 바꿔 전환한다 |
| AI | Spring AI + Gemini | 별도 Python 서버 없이 백엔드 안에서 임베딩·LLM 호출 |
| 테스트 | JUnit 5, Mockito, Testcontainers | 통합 테스트를 운영과 같은 PostgreSQL(pgvector)·Redis 위에서 실행, 동시성 재현. 메일은 가짜 발송기로 받아 본문의 코드를 검증 |
| 테스트 (프런트) | Vitest | Vite 기반이라 TS·경로 별칭을 별도 변환 설정 없이 실행. API 클라이언트의 토큰 재발급 흐름을 `fetch` 대역으로 검증 |
| API 문서 | springdoc-openapi (OpenAPI 3, Swagger UI) | 코드에서 API 문서를 생성해 프런트 타입 생성의 기준으로 쓴다. `local` 프로필에서만 기본 활성화 |
| 인프라 | Docker, GitHub Actions, AWS EC2·S3 | 컨테이너, CI/CD 경험 |

## 저장소 구조
FE와 BE를 한 저장소(모노레포)에 둔다. 1인 프로젝트에서 관리가 쉽고, 링크 하나로 전체를 보여줄 수 있다.
CI는 `paths` 필터로 바뀐 영역만 실행한다.

## FE/BE 간 API 계약
언어가 달라 타입을 직접 공유할 수 없으므로, Spring에서 springdoc으로 OpenAPI 스펙(`/v3/api-docs`)을 생성하고
프런트에서 `openapi-typescript`로 타입을 자동 생성한다. (`npm run gen:api` → `frontend/src/lib/api/schema.d.ts`)
- 생성 파일은 커밋한다. CI에서는 백엔드를 띄우지 않아 다시 만들 수 없기 때문이다.
- 화면 코드는 `schema.d.ts`를 직접 쓰지 않고 `types.ts`에서 이름을 붙인 타입을 쓴다. 스펙이 실제 응답과 다른 곳(예: nullable 누락)도 여기서 바로잡는다.

## FE/BE 호출 방식
브라우저가 Spring API를 **직접 호출**한다. (Next.js 프록시를 거치지 않음)
- CORS로 프런트 출처만 정확히 허용하고, 인증 요청은 `credentials: 'include'`로 Refresh 쿠키를 주고받는다.
- Refresh 쿠키가 `SameSite=Strict`이므로 운영에서는 프런트와 API를 같은 사이트(`doodok.xyz` / `api.doodok.xyz`)에 둔다.
- 결정 이유와 대안은 [ADR 0004](adr/0004-direct-api-calls.md)에 정리했다.
