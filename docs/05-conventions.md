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
- `global/`: 여러 도메인이 함께 쓰는 것. 설정(`config`), 예외(`exception`), 응답 형식(`response`), 보안(`security`), 공통 엔티티(`entity/BaseTimeEntity`)
- 시간 컬럼(`created_at`, `updated_at`)이 있는 엔티티는 `BaseTimeEntity`를 상속한다.

## 테스트 (backend)
- 스프링 컨텍스트를 띄우는 통합 테스트는 `@IntegrationTest`(`support` 패키지)를 붙인다. 앱 전체 + PostgreSQL 컨테이너 + MockMvc가 함께 올라오고, 모든 통합 테스트가 같은 컨텍스트를 재사용한다. H2는 쓰지 않는다.
- 서비스 로직은 Mockito로 단위 테스트한다. (DB 없이 빠르게)
- 동시성 테스트는 `@Transactional` 롤백을 쓸 수 없으므로 `@AfterEach`에서 데이터를 지운다.
- 로컬에서 테스트하려면 Docker Desktop이 실행 중이어야 한다.

## 작업 흐름
1. 이슈 생성 (템플릿 사용)
2. `develop`에서 브랜치 생성
3. 작업 후 PR → CI 통과 → 셀프 리뷰 → 머지
4. PR 본문의 "선택한 방법과 이유"는 이후 이력서·블로그 소재가 되므로 꼭 남긴다.
