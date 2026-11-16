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

## 작업 흐름
1. 이슈 생성 (템플릿 사용)
2. `develop`에서 브랜치 생성
3. 작업 후 PR → CI 통과 → 셀프 리뷰 → 머지
4. PR 본문의 "선택한 방법과 이유"는 이후 이력서·블로그 소재가 되므로 꼭 남긴다.
