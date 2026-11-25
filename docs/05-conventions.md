# 개발 컨벤션

## 브랜치
- `main`: 배포 브랜치. 직접 push 금지, PR로만 머지
- `develop`: 개발 통합 브랜치
- `feat/#이슈번호-기능명`, `fix/#이슈번호-내용`, `refactor/...`, `docs/...`, `chore/...`
- 이슈가 없으면 번호를 빼고 `feat/기능명`으로 만든다.

예: `feat/#12-signup-api`, `feat/global-exception`

## 커밋 메시지
```
<type>(<scope>): <내용>
```
- type: `feat` `fix` `refactor` `test` `docs` `chore` `style` `perf`
- scope: `be` `fe` `ai` `infra` `docs`

예: `feat(be): 회원가입 API 추가`, `test(be): 매물 동시 예약 테스트 추가`

## PR 제목
```
[타입] 내용
```
| 타입 | 용도 | 라벨 |
|---|---|---|
| `[Feat]` | 새 기능 | `feature` + 영역 |
| `[Fix]` | 버그 수정 | `bug` + 영역 |
| `[Refactor]` | 동작 변경 없는 구조 개선 | `refactor` + 영역 |
| `[Test]` | 테스트 추가·수정 | 영역 |
| `[Docs]` | 문서 | `docs` |
| `[Chore]` | 설정, 의존성, CI | `infra` |

예: `[Feat] 회원가입·로그인 API 구현` (라벨: `feature`, `BE`)

## 이슈 라벨
영역: `BE` `FE` `AI` `infra` `docs` / 종류: `feature` `bug` `refactor`

## 에러 처리 (backend)
- 비즈니스 규칙 위반은 `BusinessException(ErrorCode)`을 던진다. 컨트롤러에서 try-catch로 응답을 만들지 않는다.
- 공통 에러는 `global.exception.CommonErrorCode`, 도메인 에러는 각 도메인 패키지에 `XxxErrorCode` enum을 만들어 `ErrorCode`를 구현한다.
  - 예: `domain.listing.exception.ListingErrorCode.LISTING_ALREADY_RESERVED`
- 새 에러 코드를 추가하면 `docs/06-api-spec.md`의 에러 코드 목록에도 추가한다.
- 응답 형식은 `GlobalExceptionHandler`가 `{ code, message, errors }`로 통일한다.
- 목록 API는 `CursorResponse.fromFetched(...)`로 `size + 1`개를 조회해 다음 페이지 여부를 판단한다.

## DB 마이그레이션 (backend)
- 테이블 변경은 Flyway SQL로만 한다. JPA `ddl-auto`는 모든 환경에서 `validate`다.
- 위치: `backend/src/main/resources/db/migration/`
- 이름: `V{번호}__{설명}.sql` (밑줄 2개, 설명은 snake_case)
  - 예: `V2__create_users.sql`, `V3__create_books.sql`
  - 작업 전에 `develop`의 마지막 번호를 확인하고 다음 번호를 쓴다.
- **이미 머지된 마이그레이션 파일은 수정하지 않는다.** 바꿀 내용은 새 파일로 추가한다. (Flyway가 체크섬으로 검사해 시작 시 실패한다)
- 엔티티를 추가·수정하면 같은 PR에 마이그레이션을 함께 올린다.
- 로컬 DB를 처음부터 다시 만들려면 `docker compose down -v` 후 `docker compose up -d`

## 패키지 구조 (backend)
- `domain/{도메인}`: 엔티티, Repository, 서비스, 컨트롤러, 요청·응답 DTO, `XxxErrorCode`를 도메인별로 함께 둔다. (예: `domain/user`, `domain/auth`)
- `global/`: 여러 도메인이 함께 쓰는 것. 설정(`config`), 예외(`exception`), 응답 형식(`response`), 보안(`security`), 메일(`mail`), 공통 엔티티(`entity/BaseTimeEntity`)
- 시간 컬럼(`created_at`, `updated_at`)이 있는 엔티티는 `BaseTimeEntity`를 상속한다.

## 테스트 (backend)
- 스프링 컨텍스트를 띄우는 통합 테스트는 `@IntegrationTest`(`support` 패키지)를 붙인다. 앱 전체 + PostgreSQL·Redis 컨테이너 + MockMvc + 가짜 메일 발송기(`FakeEmailSender`)가 함께 올라오고, 모든 통합 테스트가 같은 컨텍스트를 재사용한다. H2는 쓰지 않는다.
- 통합 테스트는 `@AfterEach`에서 `IntegrationTestHelper.cleanUp()`으로 DB·Redis·보낸 메일 기록을 비운다.
- 회원가입이 필요한 테스트는 `IntegrationTestHelper.issueSignupToken(email)`으로 이메일 인증을 끝내고 signupToken을 받는다.
- 서비스 로직은 Mockito로 단위 테스트한다. (DB 없이 빠르게)
- 동시성 테스트는 `@Transactional` 롤백을 쓸 수 없으므로 `@AfterEach`에서 데이터를 지운다. 스레드 풀은 try-with-resources로 닫아 남은 작업이 끝난 뒤 정리되게 한다.

## Redis (backend)
- 키 이름은 `{기능}:{종류}:{식별자}` 형식으로 쓴다. (예: `email-verification:code:{email}`) 키 목록은 `docs/04-erd.md` 4절에 정리한다.
- 잠깐만 필요한 값에는 반드시 TTL을 건다.
- 읽고 → 판단하고 → 쓰는 작업이 여러 명령에 걸치면, 그 사이에 다른 요청이 끼어들 수 있다. Lua 스크립트로 묶어 한 번에 실행한다. (예: 카운터 증가 + 첫 TTL, 코드 비교 + 삭제)
- 로컬에서 테스트하려면 Docker Desktop이 실행 중이어야 한다.

## 환경 변수 (backend)
- 백엔드는 루트 `.env`를 자동으로 읽지 않는다. `application.yml`의 `${VAR:기본값}`과 셸·서버 환경 변수만 쓴다.
- 새 환경 변수를 추가하면 세 곳을 함께 고친다.
  1. `application.yml`: 로컬에서 바로 동작하는 기본값 (운영 전용 값이면 기본값을 비워 두고, 없으면 시작 시 실패하게 한다. 예: `JWT_SECRET`)
  2. `.env.example`: 이름과 설명, 운영 예시
  3. README: 운영에서 꼭 설정해야 하는 값이면 배포 설명에 추가
- 프런트 환경 변수는 `frontend/.env.local`에 둔다. 브라우저에서 쓰는 값만 `NEXT_PUBLIC_` 접두어를 붙인다.

## API 호출 (frontend)
- API는 `@/lib/api`에서 가져다 쓴다. `fetch`를 화면에서 직접 부르지 않는다. (`request()`가 주소, 쿠키 전송, 에러 변환, 토큰 재발급을 맡는다)
- 로그인이 필요한 API는 `request(path, { auth: true })`로 부른다. `TOKEN_EXPIRED`면 한 번 재발급한 뒤 다시 보낸다.
- 실패는 `ApiError`로 받는다. 화면 분기는 `code`로 하고, `message`는 그대로 보여 줄 수 있다. 입력값 에러는 `fieldErrors`를 쓴다.
- Access Token은 메모리(`tokenStore`)에만 둔다. localStorage·sessionStorage에 저장하지 않는다.
- 로그인 상태는 `useAuth()`로 읽는다. (`loading` / `authenticated` / `unauthenticated`)
- 백엔드 API가 바뀌면 `npm run gen:api`로 `schema.d.ts`를 다시 만들고, 바뀐 부분을 같은 PR에 커밋한다.

## 테스트 (frontend)
- Vitest를 쓰고, 테스트 파일은 대상 옆에 `*.test.ts`로 둔다.
- 네트워크는 `vi.stubGlobal("fetch", ...)`로 대신한다. 실제 서버에 요청하지 않는다.

## 작업 흐름
1. 이슈 생성 (템플릿 사용)
2. `develop`에서 브랜치 생성
3. 작업 후 PR → CI 통과 → 셀프 리뷰 → 머지
4. PR 본문의 "선택한 방법과 이유"는 이후 이력서·블로그 소재가 되므로 꼭 남긴다.
