# 개발 컨벤션

## 브랜치
- `main`: 배포 브랜치. 직접 push 금지, PR로만 머지
- `develop`: 개발 통합 브랜치
- `feat/#이슈번호-기능명`, `fix/#이슈번호-내용`, `refactor/...`, `docs/...`, `chore/...`

예: `feat/#12-signup-api`

## 커밋 메시지
```
<type>(<scope>): <내용>
```
- type: `feat` `fix` `refactor` `test` `docs` `chore` `style` `perf`
- scope: `be` `fe` `ai` `infra` `docs`

예: `feat(be): 회원가입 API 추가`, `test(be): 매물 동시 예약 테스트 추가`

## 이슈 라벨
영역: `BE` `FE` `AI` `infra` `docs` / 종류: `feature` `bug` `refactor`

## 작업 흐름
1. 이슈 생성 (템플릿 사용)
2. `develop`에서 브랜치 생성
3. 작업 후 PR → CI 통과 → 셀프 리뷰 → 머지
4. PR 본문의 "선택한 방법과 이유"는 이후 이력서·블로그 소재가 되므로 꼭 남긴다.
