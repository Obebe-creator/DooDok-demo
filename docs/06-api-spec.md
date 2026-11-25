# API 명세 (초안)

> 두독(DooDok) REST API 초안 v2.2
> MVP 기능만 정의했다. 구현하면서 확정되는 내용은 Swagger(OpenAPI) 문서가 최종 기준이 되고, 이 문서는 설계 의도를 남기는 용도로 쓴다.
> 로컬 실행 시 OpenAPI JSON은 `http://localhost:8080/v3/api-docs`, Swagger UI는 `http://localhost:8080/swagger-ui.html`에서 볼 수 있다. (`local` 프로필)

## 1. 공통 규칙

### 기본 정보
- Base URL: `/api`
- 형식: `application/json` (UTF-8)
- 시간: ISO 8601, UTC 오프셋 포함 (`2026-11-15T16:25:00+09:00`)
- 금액: 원 단위 정수

### 인증
- 로그인하면 **Access Token은 응답 본문**, **Refresh Token은 HttpOnly 쿠키**로 내려준다.
  - Access Token은 짧게(30분), 요청 헤더에 담는다: `Authorization: Bearer {accessToken}`
  - Refresh Token은 JS에서 읽을 수 없게 해 XSS로 탈취되는 것을 막는다.
- Access Token이 만료되면 보호된 API는 `401 TOKEN_EXPIRED`를 반환한다. 프런트는 이 코드를 받으면 `POST /auth/refresh`로 재발급한 뒤 원래 요청을 다시 보낸다. 그 밖의 `401`(`UNAUTHORIZED`)은 로그인 화면으로 보낸다.
- 표의 **인증** 열: `-` 불필요, `O` 필요, `△` 선택 (로그인하면 추가 정보 제공)

### 브라우저 호출 (CORS · 쿠키)
- 브라우저는 API를 직접 호출한다. 허용된 프런트 출처(`CORS_ALLOWED_ORIGINS`)만 CORS를 통과하고, 그 밖의 출처는 거부된다. CORS는 `/api/**`에 적용된다.
- Refresh 쿠키를 주고받는 요청(`/auth/login`, `/auth/refresh`, `/auth/logout`)은 `credentials: 'include'`로 보낸다.
- Refresh 쿠키는 `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`다. **다른 사이트에서 보낸 요청에는 전송되지 않으므로** 프런트와 API를 같은 사이트(`https://doodok.xyz` / `https://api.doodok.xyz`)에 둔다.
- 허용 헤더는 `Authorization`, `Content-Type`이고, 프런트가 읽을 수 있는 응답 헤더는 `Location`이다.
- 프리플라이트(`OPTIONS`) 결과는 브라우저가 1시간 캐시한다(`Access-Control-Max-Age: 3600`). 매 요청 앞의 `OPTIONS` 왕복을 줄이기 위해서다. (Chrome은 최대 2시간까지만 적용)
- Refresh Token은 재발급 때마다 교체되므로, 프런트는 같은 쿠키로 재발급을 동시에 두 번 보내지 않는다. (뒤 요청은 `401`이 되어 로그아웃된다)

### 성공 응답
래퍼 없이 리소스를 그대로 반환한다. HTTP 상태 코드로 성공 여부를 이미 알 수 있어서 `success: true` 같은 필드는 두지 않는다.

```json
{
  "id": 12,
  "title": "데미안",
  "price": 6000
}
```

### 목록 응답 (커서 페이지네이션)
매물·독후감처럼 새 글이 계속 쌓이는 목록은 **커서 방식**을 쓴다. 오프셋 방식은 스크롤 중 새 글이 생기면 항목이 중복되거나 빠지기 때문.

```json
{
  "items": [ { "id": 31 }, { "id": 30 } ],
  "nextCursor": "30",
  "hasNext": true
}
```
- 요청: `?cursor=30&size=20` (첫 페이지는 `cursor` 생략, `size` 기본 20, 최대 50)

### 에러 응답
```json
{
  "code": "LISTING_ALREADY_RESERVED",
  "message": "이미 예약된 판매글입니다.",
  "errors": []
}
```
- `code`: 프런트에서 분기할 때 쓰는 고정 값
- `message`: 사용자에게 보여줄 문장
- `errors`: 입력값 검증 실패 시 필드별 사유 (`[{ "field": "price", "reason": "0 이상이어야 합니다." }]`)
- 예상하지 못한 서버 에러는 내부 메시지를 노출하지 않고 고정 문구를 반환한다.

### 주요 상태 코드
| 코드 | 의미 |
|---|---|
| 200 | 조회·수정 성공 |
| 201 | 생성 성공 (`Location` 헤더 포함) |
| 204 | 삭제 성공 (본문 없음) |
| 400 | 입력값 오류 |
| 401 | 인증 필요, 토큰 만료 |
| 403 | 권한 없음 (남의 글 수정 등) |
| 404 | 리소스 없음 |
| 409 | 상태 충돌 (이미 예약됨, 중복 가입 등) |
| 429 | 요청 횟수 제한 초과 (인증 코드 재발송 등) |
| 502 | 외부 API(도서 검색, AI) 오류 |

## 2. 엔드포인트 요약

| 영역 | 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|---|
| 인증 | POST | `/auth/email-verifications` | 이메일 인증 코드 발송 | - |
| | POST | `/auth/email-verifications/confirm` | 인증 코드 확인, signupToken 발급 | - |
| | POST | `/auth/signup` | 회원가입 (signupToken 필요) | - |
| | POST | `/auth/login` | 로그인 | - |
| | POST | `/auth/refresh` | 토큰 재발급 | 쿠키 |
| | POST | `/auth/logout` | 로그아웃 (Refresh 쿠키) | - |
| 회원 | GET | `/users/me` | 내 정보 | O |
| | PATCH | `/users/me` | 내 정보 수정 | O |
| | GET | `/users/{userId}` | 다른 회원 프로필 | - |
| 도서 | GET | `/books/search` | 외부 도서 검색 | O |
| | POST | `/books` | ISBN으로 도서 등록(없으면 생성) | O |
| | GET | `/books/{bookId}` | 도서 상세 | - |
| | GET | `/books/{bookId}/listings` | 이 책의 판매 중 매물 | - |
| | GET | `/books/{bookId}/reviews` | 이 책의 공개 독후감 | - |
| 독후감 | POST | `/reviews` | 독후감 작성 | O |
| | GET | `/reviews/{reviewId}` | 독후감 상세 | △ |
| | PATCH | `/reviews/{reviewId}` | 독후감 수정 | O |
| | DELETE | `/reviews/{reviewId}` | 독후감 삭제 | O |
| | GET | `/users/me/reviews` | 내 독후감 목록 | O |
| 매물 | GET | `/listings` | 매물 목록·검색 | - |
| | POST | `/listings` | 판매글 등록 | O |
| | GET | `/listings/{listingId}` | 판매글 상세 | △ |
| | PATCH | `/listings/{listingId}` | 판매글 수정 | O |
| | DELETE | `/listings/{listingId}` | 판매글 삭제 | O |
| | PATCH | `/listings/{listingId}/visibility` | 숨김/다시 판매 | O |
| | GET | `/users/me/listings` | 내 판매글 목록 | O |
| 이미지 | POST | `/uploads/presigned-url` | 이미지 업로드 URL 발급 | O |
| 채팅 | POST | `/listings/{listingId}/chat-rooms` | 채팅 시작 (있으면 기존 방) | O |
| | GET | `/chat-rooms` | 내 채팅방 목록 | O |
| | GET | `/chat-rooms/{chatRoomId}/messages` | 메시지 이력 | O |
| | PATCH | `/chat-rooms/{chatRoomId}/read` | 읽음 처리 | O |
| | WS | `/ws` (STOMP) | 실시간 메시지 송수신 | O |
| 거래 | POST | `/listings/{listingId}/transactions` | 예약 (판매자가 채팅 상대 선택) | O |
| | POST | `/transactions/{transactionId}/complete` | 거래 완료 | O |
| | POST | `/transactions/{transactionId}/cancel` | 예약 취소 | O |
| | GET | `/users/me/transactions` | 내 거래 내역 | O |
| 거래 후기 | POST | `/transactions/{transactionId}/trade-reviews` | 거래 후기 작성 | O |
| | GET | `/users/{userId}/trade-reviews` | 받은 거래 후기 | - |
| 내 서재 | GET | `/users/me/bookshelf` | 내 서재 목록 | O |
| | POST | `/users/me/bookshelf` | 서재에 담기 | O |
| | PATCH | `/users/me/bookshelf/{itemId}` | 상태·입고 알림 변경 | O |
| | DELETE | `/users/me/bookshelf/{itemId}` | 서재에서 빼기 | O |
| 알림 | GET | `/notifications` | 알림 목록 | O |
| | GET | `/notifications/unread-count` | 안 읽은 알림 수 | O |
| | PATCH | `/notifications/{notificationId}/read` | 읽음 처리 | O |
| | PATCH | `/notifications/read-all` | 모두 읽음 | O |
| | GET | `/notifications/stream` | 실시간 알림 (SSE) | O |
| 추천 | GET | `/recommendations/me` | 내 취향 기반 추천 | O |
| | GET | `/books/{bookId}/similar` | 비슷한 책 | - |
| 운영 | GET | `/actuator/health` | 헬스체크 (Base URL 밖) | - |

## 3. 상세

### 3-1. 인증

#### 가입 흐름 (가입 전 이메일 인증)
```
① POST /auth/email-verifications          { email }          → 메일로 6자리 코드 발송
② POST /auth/email-verifications/confirm  { email, code }    → signupToken 발급
③ POST /auth/signup                       { signupToken, ... } → 토큰의 인증된 이메일로 가입
```
- 이메일을 인증해야만 가입할 수 있다. 이후 소셜 로그인(구글·카카오)에서 같은 이메일의 계정을 연결할 때, 자체 가입 이메일이 인증된 상태임을 보장하기 위해서다.
- 인증 데이터(코드, signupToken)는 Redis에 TTL로 저장하고, 만료되면 자동으로 사라진다.

#### POST `/auth/email-verifications` 인증 코드 발송
```json
// Request
{ "email": "reader@doodok.com" }

// 202 Accepted
{ "expiresIn": 300, "resendAvailableIn": 60 }
```
- 이메일: 형식 검사, 최대 255자. 앞뒤 공백을 지우고 **소문자로 바꾼 값**을 기준으로 검사·발송·저장한다. (`Reader@...`와 `reader@...`는 같은 이메일)
- 메일은 비동기로 보내서 202로 응답한다. 코드는 숫자 6자리, 유효 시간 5분(`expiresIn`, 초)
- 새 코드를 발급하면 이전 코드와 틀린 횟수는 무효가 된다.
- 제한: 같은 이메일로 60초에 1번(`resendAvailableIn`, 초), 하루 10번 / 같은 IP에서 1시간에 20번
- 이미 가입된 이메일이면 코드를 보내지 않고 `409 DUPLICATE_EMAIL`로 바로 알려준다.
- **가입 여부 노출(계정 열거)은 감수한 위험이다.**
  - 이 API는 로그인 없이 누구나 부를 수 있어서, 응답 차이로 가입 여부를 알 수 있다.
  - 응답을 똑같이 주고 안내 메일을 보내는 방식도 검토했지만, 사용자가 메일함을 열어봐야 이미 가입한 것을 알 수 있어 UX를 우선했다. 중고책 서비스 가입 여부는 민감한 정보가 아니라고 판단했다.
  - 대신 IP당 요청 수를 제한해(가입 여부 확인 전에 검사) 대량 조회를 막고, 열거한 목록으로 비밀번호를 대입하는 공격은 로그인 시도 제한으로 막는다.
- 에러: `400 INVALID_INPUT`, `409 DUPLICATE_EMAIL`, `429 EMAIL_VERIFICATION_IP_LIMIT`, `429 EMAIL_VERIFICATION_TOO_FREQUENT`, `429 EMAIL_VERIFICATION_DAILY_LIMIT`

#### POST `/auth/email-verifications/confirm` 인증 코드 확인
```json
// Request
{ "email": "reader@doodok.com", "code": "000916" }

// 200 OK
{ "signupToken": "WAYr2oO2RCWHYWN_7WJcDkg7s8s1ugHaDXs-yHtZIHw", "expiresIn": 600 }
```
- 코드가 맞으면 signupToken(유효 10분, 한 번만 사용)을 발급한다. 코드는 한 번 확인하면 폐기된다. 같은 코드로 동시에 확인해도 토큰은 하나만 발급된다.
- 코드를 5번 틀리면 코드를 폐기한다. 새 코드를 요청해야 한다.
- 에러: `400 INVALID_INPUT`(코드가 숫자 6자리가 아님), `400 EMAIL_VERIFICATION_CODE_EXPIRED`(만료, 요청한 적 없음, 이미 사용), `400 EMAIL_VERIFICATION_CODE_MISMATCH`, `429 EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED`

#### POST `/auth/signup` 회원가입
```json
// Request
{ "signupToken": "WAYr2oO2RCWHYWN_7WJcDkg7s8s1ugHaDXs-yHtZIHw", "password": "Passw0rd!", "nickname": "책읽는곰" }

// 201 Created  (+ Location: /api/users/1)
{ "id": 1, "email": "reader@doodok.com", "nickname": "책읽는곰" }
```
- 이메일은 요청에 담지 않는다. signupToken에 연결된 **인증된 이메일**로 가입한다. (A 메일로 인증하고 B 메일로 가입하는 우회 방지)
- 비밀번호: 8~20자, **ASCII 영문·숫자·키보드 특수문자만** 허용하고 세 가지를 모두 포함. 공백·한글·이모지는 쓸 수 없다. BCrypt로 해시해 저장한다. (BCrypt는 72바이트까지만 처리하므로, ASCII로 제한해 최대 20바이트로 유지한다)
- 닉네임: 2~12자, 한글·영문·숫자·밑줄(`_`)만
- 성공하면 `Location: /api/users/{id}` 헤더를 함께 준다.
- signupToken은 **가입에 성공했을 때만** 소모된다. 닉네임 중복 등으로 실패하면 같은 토큰으로 다시 시도할 수 있다.
- 이메일·닉네임 중복은 탈퇴하지 않은 회원 기준이다. 탈퇴한 회원의 이메일·닉네임으로는 다시 가입할 수 있다.
- 에러: `400 INVALID_INPUT`, `400 INVALID_SIGNUP_TOKEN`(만료, 이미 사용, 존재하지 않음), `409 DUPLICATE_EMAIL`, `409 DUPLICATE_NICKNAME`
  - 여기서 `DUPLICATE_EMAIL`은 인증을 마친 뒤 같은 이메일로 다른 가입이 먼저 끝난 경우에만 나온다.

#### POST `/auth/login` 로그인
```json
// Request
{ "email": "reader@doodok.com", "password": "Passw0rd!" }

// 200 OK  (+ Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth)
{ "accessToken": "eyJ...", "expiresIn": 1800, "user": { "id": 1, "nickname": "책읽는곰" } }
```
- 에러: `401 INVALID_CREDENTIALS` (이메일과 비밀번호 중 무엇이 틀렸는지 구분하지 않는다)
- 비밀번호가 UTF-8 기준 72바이트를 넘으면 해시 비교 없이 같은 에러를 반환한다.
- Redis에서 첫 집계부터 15분 동안 요청 IP당 50회, 정규화된 계정당 10회로 제한한다. IP 한도를 먼저 검사하며, 이 단계에서 `429 LOGIN_ATTEMPTS_EXCEEDED`로 거부된 요청은 계정 횟수에 포함되지 않는다.
- IP 한도와 이메일 기본 검사(빈 값·길이)를 통과한 시도는 로그인 성공·실패와 관계없이 계정별로 집계한다. 계정 한도를 넘으면 같은 `429`를 반환한다. 계정 한도는 요청 IP와 독립적이므로 제3자의 시도로 소진되면 정상 사용자도 집계 구간이 끝날 때까지 로그인할 수 없다.

#### POST `/auth/refresh` 토큰 재발급
- 쿠키의 Refresh Token으로 새 Access Token을 발급하고, Refresh Token도 새로 바꾼다(rotation). 사용한 토큰은 폐기한다.
- Refresh Token은 발급 시점부터 14일간 유효하다.
- 에러: `401 INVALID_REFRESH_TOKEN`

#### POST `/auth/logout` 로그아웃
- Refresh 쿠키에 해당하는 토큰을 폐기하고 쿠키를 삭제한다. Access Token이 만료됐거나 없어도 호출할 수 있다. 쿠키가 없거나 이미 폐기돼도 `204 No Content`와 쿠키 만료 헤더를 반환한다.
- 이미 발급된 Access Token은 만료까지 유효하다.

### 3-2. 회원

#### GET `/users/me` 내 정보
```json
{ "id": 1, "email": "reader@doodok.com", "nickname": "책읽는곰", "profileImageUrl": null, "createdAt": "2026-11-15T16:25:00+09:00" }
```

#### PATCH `/users/me` 내 정보 수정
```json
{ "nickname": "책읽는호랑이", "profileImageUrl": "https://..." }
```
- 보낸 필드만 수정한다.

#### GET `/users/{userId}` 다른 회원 프로필
```json
{
  "id": 2, "nickname": "다음장", "profileImageUrl": null,
  "sellingCount": 5, "publicReviewCount": 12,
  "trade": { "tradeCount": 14, "goodRate": 93 }
}
```
- 이메일 등 개인정보는 포함하지 않는다.
- `trade.goodRate`: 받은 거래 후기 중 `GOOD` 비율(%). 받은 후기가 없으면 `null`

### 3-3. 도서

#### GET `/books/search?query=데미안&page=1` 외부 도서 검색
판매글·독후감을 쓸 때 책을 고르는 용도. 외부 API(카카오) 결과를 그대로 보여주고 **DB에는 저장하지 않는다**.
```json
{
  "items": [
    { "isbn13": "9788937460449", "title": "데미안", "author": "헤르만 헤세", "publisher": "민음사", "coverImageUrl": "https://...", "listPrice": 9000 }
  ],
  "page": 1,
  "hasNext": true
}
```
- 외부 API 페이지 방식을 따르므로 이 API만 페이지 번호를 쓴다.
- 에러: `502 BOOK_SEARCH_UNAVAILABLE`

#### POST `/books` 도서 등록
사용자가 책을 고르면 호출한다. 이미 있으면 기존 도서를, 없으면 외부 API에서 상세 정보를 가져와 저장한 뒤 반환한다.
```json
// Request
{ "isbn13": "9788937460449" }

// 200 OK (이미 있음) 또는 201 Created (새로 저장)
{ "id": 7, "isbn13": "9788937460449", "title": "데미안", "author": "헤르만 헤세", "publisher": "민음사", "publishedDate": "2000-12-20", "listPrice": 9000, "coverImageUrl": "https://...", "category": "소설" }
```
- 같은 ISBN 요청이 동시에 와도 `isbn13` 유니크 제약으로 하나만 저장된다. 충돌 시 기존 행을 다시 조회해 반환한다.

#### GET `/books/{bookId}` 도서 상세
```json
{
  "id": 7, "isbn13": "9788937460449", "title": "데미안", "author": "헤르만 헤세",
  "description": "...", "coverImageUrl": "https://...",
  "stats": { "onSaleCount": 3, "minPrice": 4000, "publicReviewCount": 18, "averageRating": 4.3 }
}
```

#### GET `/books/{bookId}/listings` 이 책의 판매 중 매물
- 쿼리: `sort=price_asc|latest` (기본 `price_asc`), 커서 페이지네이션
- 응답 항목은 매물 목록과 같다.

#### GET `/books/{bookId}/reviews` 이 책의 공개 독후감
- `PUBLIC`만 반환한다. 스포일러 글은 `content` 대신 `hasSpoiler: true`와 미리보기 없이 내려주고, 상세 조회에서 본문을 보여준다.

### 3-4. 독후감

#### POST `/reviews` 독후감 작성
```json
// Request
{ "bookId": 7, "content": "...", "rating": 5, "readDate": "2026-11-10", "visibility": "PUBLIC", "hasSpoiler": false }

// 201 Created
{ "id": 40, "bookId": 7, "content": "...", "rating": 5, "readDate": "2026-11-10", "visibility": "PUBLIC", "hasSpoiler": false, "createdAt": "..." }
```
- 같은 책에 여러 번 작성할 수 있다.
- 작성하면 해당 책이 내 서재에 `READ`로 자동 등록된다.
- 저장 후 임베딩 생성은 비동기로 처리한다. 응답 시간에 영향을 주지 않는다.
- 검증: `content` 10~5,000자, `rating` 1~5, `readDate`는 오늘 이전

#### GET `/reviews/{reviewId}` 독후감 상세
- `PRIVATE` 글은 작성자만 조회할 수 있다. 다른 사람이 요청하면 `404 REVIEW_NOT_FOUND` (존재 여부를 숨기기 위해 403 대신 404)

#### PATCH `/reviews/{reviewId}` / DELETE `/reviews/{reviewId}`
- 작성자만 가능 (`403 FORBIDDEN`)
- 판매글에 첨부된 독후감을 삭제하면 판매글의 첨부만 해제된다.
- 내용을 수정하면 임베딩을 다시 만든다.

#### GET `/users/me/reviews` 내 독후감 목록
- 쿼리: `bookId` (특정 책의 내 독후감만), 커서 페이지네이션

### 3-5. 매물

#### GET `/listings` 매물 목록·검색
| 쿼리 | 설명 | 예시 |
|---|---|---|
| `query` | 제목·저자 검색 | `헤세` |
| `status` | 기본 `ON_SALE` | `ON_SALE,RESERVED` |
| `condition` | 상태 등급 | `S,A` |
| `tradeMethod` | 거래 방법 (`BOTH`인 판매글은 둘 다에 포함) | `DIRECT`, `DELIVERY` |
| `minPrice`, `maxPrice` | 가격 범위 | `3000`, `10000` |
| `sort` | `latest`(기본), `price_asc`, `price_desc` | |
| `cursor`, `size` | 페이지네이션 | |

```json
{
  "items": [
    {
      "id": 31, "title": "데미안 깨끗하게 읽었어요", "price": 5000, "conditionGrade": "A", "status": "ON_SALE",
      "tradeMethod": "BOTH", "shippingFee": 3000,
      "thumbnailUrl": "https://...",
      "book": { "id": 7, "title": "데미안", "author": "헤르만 헤세" },
      "hasReview": true,
      "chatCount": 2,
      "createdAt": "..."
    }
  ],
  "nextCursor": "30", "hasNext": true
}
```
- 가격순 정렬은 값이 같을 수 있어 커서를 `가격_id` 조합으로 만든다.

#### POST `/listings` 판매글 등록
```json
// Request
{
  "bookId": 7, "title": "데미안 깨끗하게 읽었어요", "price": 5000, "conditionGrade": "A",
  "description": "밑줄 없습니다.", "reviewId": 40,
  "tradeMethod": "BOTH", "tradePlace": "신촌역 2번 출구", "shippingFee": 3000,
  "imageUrls": ["https://.../1.jpg", "https://.../2.jpg"]
}
// 201 Created → 판매글 상세와 같은 형태
```
- 거래 방법은 판매자가 고른다.

  | `tradeMethod` | `tradePlace` | `shippingFee` |
  |---|---|---|
  | `DIRECT` (직거래) | 필수 | 보내지 않음 |
  | `DELIVERY` (택배) | 보내지 않음 | 필수 (`0`이면 배송비 포함) |
  | `BOTH` | 필수 | 필수 |
- 직거래 장소는 자유 입력이며 동네 인증은 하지 않는다. 배송 추적도 하지 않는다.
- 이미지: 1~5장, 먼저 `/uploads/presigned-url`로 올린 URL을 보낸다.
- `reviewId`: 선택. 본인이 쓴 **같은 책** 독후감이어야 하고, 다른 판매글에 첨부되지 않은 것이어야 한다.
- 등록 후, 이 책을 서재에 담고 입고 알림을 켜둔 사용자에게 알림을 보낸다. (비동기)
- 에러: `400 INVALID_TRADE_OPTION`, `400 INVALID_REVIEW_ATTACHMENT`, `409 REVIEW_ALREADY_ATTACHED`

#### GET `/listings/{listingId}` 판매글 상세
```json
{
  "id": 31, "title": "...", "price": 5000, "conditionGrade": "A", "description": "...", "status": "ON_SALE",
  "tradeMethod": "BOTH", "tradePlace": "신촌역 2번 출구", "shippingFee": 3000,
  "images": [ { "url": "https://...", "sortOrder": 0 } ],
  "book": { "id": 7, "isbn13": "...", "title": "데미안", "author": "헤르만 헤세", "listPrice": 9000 },
  "seller": { "id": 2, "nickname": "다음장", "tradeCount": 14, "goodRate": 93 },
  "review": { "id": 40, "rating": 5, "content": "...", "hasSpoiler": false },
  "isMine": false,
  "myChatRoomId": null,
  "createdAt": "..."
}
```
- `review`: 첨부한 독후감. 비공개로 바뀌었으면 `null`
- `seller.goodRate`: 받은 거래 후기 중 `GOOD` 비율(%)
- `isMine`, `myChatRoomId`: 로그인한 경우에만 의미 있음. 이미 문의한 채팅방이 있으면 그 ID

#### PATCH `/listings/{listingId}` 판매글 수정
- 판매자만, `ON_SALE` 상태에서만 수정 가능 (`409 LISTING_NOT_EDITABLE`)

#### DELETE `/listings/{listingId}` 판매글 삭제
- 예약 중이면 삭제할 수 없다 (`409 LISTING_RESERVED`). 소프트 삭제.

#### PATCH `/listings/{listingId}/visibility` 숨김/다시 판매
```json
{ "hidden": true }
```
- `ON_SALE ↔ HIDDEN`만 바꾼다. 예약·판매 완료 상태는 거래 API로만 바뀐다.

### 3-6. 이미지

#### POST `/uploads/presigned-url` 업로드 URL 발급
```json
// Request
{ "purpose": "LISTING", "fileName": "book.jpg", "contentType": "image/jpeg" }

// 200 OK
{ "uploadUrl": "https://s3...?X-Amz-Signature=...", "imageUrl": "https://cdn.../listings/uuid.jpg", "expiresIn": 300 }
```
- 클라이언트가 S3에 직접 올려 서버가 파일을 중계하지 않는다.
- `purpose`: `LISTING`(판매글), `CHAT`(채팅 이미지), `PROFILE`(프로필). S3 경로를 나누는 데 쓴다.
- 허용 형식: jpeg, png, webp / 최대 10MB

### 3-7. 채팅

채팅방 목록·메시지 이력은 **REST**, 실시간 송수신은 **WebSocket(STOMP)**으로 나눈다.

#### POST `/listings/{listingId}/chat-rooms` 채팅 시작
구매 희망자가 "채팅하기"를 누를 때 호출한다. 이미 방이 있으면 기존 방을 반환한다.
```json
// 200 OK (기존 방) 또는 201 Created (새 방)
{ "id": 5, "listingId": 31, "sellerId": 2, "buyerId": 1, "createdAt": "..." }
```
- `(listing_id, buyer_id)` 유니크로 동시에 눌러도 방은 하나만 생긴다.
- 에러: `400 CANNOT_CHAT_OWN_LISTING`, `409 LISTING_NOT_AVAILABLE` (판매 완료·숨김)

#### GET `/chat-rooms` 내 채팅방 목록
```json
{
  "items": [
    {
      "id": 5,
      "listing": { "id": 31, "title": "...", "thumbnailUrl": "https://...", "status": "ON_SALE", "price": 5000 },
      "opponent": { "id": 2, "nickname": "다음장" },
      "lastMessage": { "type": "TEXT", "content": "내일 가능할까요?", "createdAt": "..." },
      "unreadCount": 2
    }
  ],
  "nextCursor": "...", "hasNext": false
}
```
- 쿼리: `listingId` (판매자가 자기 판매글의 채팅방만 볼 때), 커서 페이지네이션
- 최근 메시지 순 정렬

#### GET `/chat-rooms/{chatRoomId}/messages` 메시지 이력
- 최신순 커서 페이지네이션 (`cursor`는 메시지 ID). 위로 스크롤하면 이전 메시지를 불러온다.
```json
{
  "items": [
    { "id": 120, "senderId": 1, "type": "TEXT", "content": "내일 가능할까요?", "createdAt": "..." },
    { "id": 119, "senderId": null, "type": "SYSTEM", "content": "예약이 취소되었습니다.", "createdAt": "..." }
  ],
  "nextCursor": "119", "hasNext": true
}
```
- 참여자(판매자, 구매자)만 조회 가능 (`403 NOT_CHAT_PARTICIPANT`)

#### PATCH `/chat-rooms/{chatRoomId}/read` 읽음 처리
```json
{ "lastReadMessageId": 120 }
```
- 마지막으로 읽은 메시지 ID만 갱신한다. 상대방에게 읽음 이벤트를 보낸다.

#### WebSocket (STOMP)
| 구분 | 경로 | 설명 |
|---|---|---|
| 연결 | `/ws` | CONNECT 헤더에 `Authorization: Bearer {accessToken}` |
| 구독 | `/sub/chat-rooms/{chatRoomId}` | 해당 방의 새 메시지, 읽음 이벤트 수신 |
| 발행 | `/pub/chat-rooms/{chatRoomId}/messages` | 메시지 전송 |

```json
// 발행 (클라이언트 → 서버)
{ "type": "TEXT", "content": "내일 가능할까요?", "clientMessageId": "c-8f2a" }

// 수신 (서버 → 구독자)
{ "event": "MESSAGE", "message": { "id": 121, "senderId": 1, "type": "TEXT", "content": "...", "createdAt": "..." }, "clientMessageId": "c-8f2a" }
{ "event": "READ", "userId": 2, "lastReadMessageId": 121 }
```
- 메시지는 **DB에 먼저 저장한 뒤** 구독자에게 보낸다. 연결이 끊겨도 이력 API로 복구할 수 있다.
- `clientMessageId`: 보낸 사람 화면에서 "전송 중" 메시지를 실제 메시지로 바꾸는 데 쓴다. 재전송으로 같은 메시지가 두 번 저장되는 것도 막는다.
- 이미지 메시지는 `/uploads/presigned-url`(`purpose: CHAT`)로 올린 뒤 URL을 `content`에 담는다.
- 상대방이 채팅방을 보고 있지 않으면 알림(`CHAT_MESSAGE`)을 만든다.
- 서버를 여러 대로 늘리는 단계에서는 Redis Pub/Sub으로 서버 간 메시지를 전달한다.

### 3-8. 거래

판매글 상태는 거래 API로만 바뀐다.
```
ON_SALE ──예약──▶ RESERVED ──완료──▶ SOLD
   ▲                  │
   └──────취소────────┘
```
상태가 바뀔 때마다 해당 채팅방에 `SYSTEM` 메시지를 남기고, 상대방에게 알림을 보낸다.

#### POST `/listings/{listingId}/transactions` 예약
**판매자가 채팅한 구매자 중 한 명을 골라** 예약한다.
```json
// Request
{ "chatRoomId": 5, "tradeMethod": "DIRECT", "agreedPrice": 4500 }

// 201 Created
{ "id": 9, "listingId": 31, "chatRoomId": 5, "buyerId": 1, "tradeMethod": "DIRECT", "agreedPrice": 4500, "status": "RESERVED", "reservedAt": "..." }
```
- 판매자만 가능. `chatRoomId`는 이 판매글의 채팅방이어야 한다. 구매자는 채팅방에서 정해진다.
- `tradeMethod`: 판매글이 허용한 방법 중 하나 (`BOTH`면 둘 중 하나로 확정)
- `agreedPrice`: 생략하면 판매글 가격
- 처리: 조건부 UPDATE(`WHERE status = 'ON_SALE'`)로 판매글을 먼저 선점하고, 성공한 경우에만 거래를 만든다. 판매자가 두 채팅방에서 동시에 예약해도 하나만 성공하며, 부분 유니크 인덱스로 한 번 더 막는다.
- 에러: `403 FORBIDDEN`, `400 INVALID_CHAT_ROOM`, `400 INVALID_TRADE_OPTION`, `409 LISTING_ALREADY_RESERVED`

#### POST `/transactions/{transactionId}/complete` 거래 완료
- 판매자만 가능. 거래 `COMPLETED`, 판매글 `SOLD`
- 완료되면 양쪽에 거래 후기 작성 알림을 보낸다.

#### POST `/transactions/{transactionId}/cancel` 예약 취소
- 판매자, 구매자 모두 가능. 거래 `CANCELED`, 판매글 `ON_SALE`로 복구
- 에러: `409 TRANSACTION_NOT_CANCELABLE` (이미 완료된 거래)

#### GET `/users/me/transactions` 내 거래 내역
- 쿼리: `role=BUYER|SELLER`, `status`, 커서 페이지네이션
- 각 항목에 `myTradeReviewWritten`(내가 후기를 썼는지)을 포함한다.

### 3-9. 거래 후기

#### POST `/transactions/{transactionId}/trade-reviews` 거래 후기 작성
```json
// Request
{ "rating": "GOOD", "content": "약속 시간을 잘 지켜주셨어요." }

// 201 Created
{ "id": 3, "transactionId": 9, "revieweeId": 2, "rating": "GOOD", "content": "...", "createdAt": "..." }
```
- 거래 당사자만, 거래가 `COMPLETED`일 때만 작성할 수 있다. 상대방(`reviewee`)은 서버가 정한다.
- `rating`: `GOOD`, `NORMAL`, `BAD` / `content`: 선택, 최대 200자
- 에러: `409 TRADE_NOT_COMPLETED`, `409 TRADE_REVIEW_ALREADY_WRITTEN`

#### GET `/users/{userId}/trade-reviews` 받은 거래 후기
```json
{
  "summary": { "tradeCount": 14, "good": 13, "normal": 1, "bad": 0 },
  "items": [ { "id": 3, "reviewer": { "id": 1, "nickname": "책읽는곰" }, "rating": "GOOD", "content": "...", "createdAt": "..." } ],
  "nextCursor": "2", "hasNext": false
}
```
- `BAD` 후기의 `content`는 받은 본인에게만 보여준다. 다른 사람에게는 개수만 공개한다.

### 3-10. 내 서재

#### GET `/users/me/bookshelf` 내 서재
- 쿼리: `status=WANT_TO_READ|READ`, 커서 페이지네이션
```json
{
  "items": [
    {
      "id": 15, "status": "WANT_TO_READ", "notifyOnListing": true,
      "book": { "id": 12, "title": "수레바퀴 아래서", "author": "헤르만 헤세", "coverImageUrl": "https://..." },
      "onSaleCount": 2, "lowestPrice": 4500,
      "myReviewCount": 0,
      "createdAt": "..."
    }
  ],
  "nextCursor": "14", "hasNext": false
}
```

#### POST `/users/me/bookshelf` 서재에 담기
```json
// Request
{ "bookId": 12, "status": "WANT_TO_READ", "notifyOnListing": true }
// 201 Created
```
- 이미 담긴 책이면 `409 BOOKSHELF_ITEM_EXISTS`
- 독후감을 쓰면 해당 책이 자동으로 `READ`로 담기거나 바뀐다.

#### PATCH `/users/me/bookshelf/{itemId}` 상태·알림 변경
```json
{ "status": "READ", "notifyOnListing": false }
```

#### DELETE `/users/me/bookshelf/{itemId}` 서재에서 빼기
- `204 No Content`

### 3-11. 알림

#### GET `/notifications` 알림 목록
```json
{
  "items": [
    { "id": 77, "type": "LISTING_ARRIVED", "title": "읽고 싶은 책 '수레바퀴 아래서'가 올라왔어요.", "payload": { "listingId": 52, "bookId": 12 }, "readAt": null, "createdAt": "..." }
  ],
  "nextCursor": "76", "hasNext": true
}
```

| `type` | 언제 | `payload` |
|---|---|---|
| `CHAT_MESSAGE` | 상대방이 보고 있지 않은 채팅방에 새 메시지 | `chatRoomId`, `listingId` |
| `TRANSACTION_RESERVED` | 판매자가 나와 예약 | `transactionId`, `listingId` |
| `TRANSACTION_COMPLETED` | 거래 완료 (후기 작성 유도) | `transactionId` |
| `TRANSACTION_CANCELED` | 예약 취소 | `transactionId`, `listingId` |
| `TRADE_REVIEW_RECEIVED` | 거래 후기를 받음 | `tradeReviewId` |
| `LISTING_ARRIVED` | 서재에 담은 책의 판매글 등록 | `listingId`, `bookId` |

- 채팅 메시지 알림은 같은 채팅방에서 연속으로 오면 하나로 묶는다.

#### GET `/notifications/unread-count` 안 읽은 알림 수
```json
{ "count": 3 }
```

#### PATCH `/notifications/{notificationId}/read` / PATCH `/notifications/read-all` 읽음 처리
- `204 No Content`

#### GET `/notifications/stream` 실시간 알림 (SSE)
- `Content-Type: text/event-stream`. 새 알림이 생기면 `event: notification`으로 보낸다.
- 연결이 끊기면 브라우저가 자동으로 다시 연결한다. 끊긴 동안의 알림은 목록 API로 조회한다.
- 채팅은 양방향이라 WebSocket, 알림은 서버 → 클라이언트 단방향이라 SSE로 나눴다.

### 3-12. 추천

#### GET `/recommendations/me?size=10` 내 취향 기반 추천
```json
{
  "strategy": "HYBRID",
  "items": [
    {
      "book": { "id": 12, "title": "수레바퀴 아래서", "author": "헤르만 헤세", "coverImageUrl": "https://..." },
      "score": 0.87,
      "reason": "회원님이 '데미안' 독후감에서 남긴 감상과 결이 비슷해요.",
      "lowestListing": { "id": 52, "price": 4500, "conditionGrade": "A" },
      "inMyBookshelf": false
    }
  ]
}
```
- 기본은 판매 중인 매물이 있는 책만 반환한다. `includeUnavailable=true`면 매물이 없는 책도 포함하고, 이때 `lowestListing`은 `null`이다. 화면에서는 "서재에 담고 알림 받기"로 연결한다.
- 취향 근거: 독후감(책마다 최신 1개) + 서재의 읽고 싶은 책
- 독후감과 서재가 모두 비어 있으면 `strategy: "POPULAR"`로 최근 많이 거래된 책을 반환한다.
- `reason`은 이후 LLM으로 생성한다. 초기 버전은 규칙 기반 문장 또는 `null`

#### GET `/books/{bookId}/similar?size=6` 비슷한 책
- 도서 벡터(META, REVIEW_AVG) 기준 유사도 순. 판매 중 매물이 없는 책도 포함하고 `onSaleCount`를 함께 준다.

## 4. 에러 코드 목록

| HTTP | code | 설명 |
|---|---|---|
| 400 | `INVALID_INPUT` | 입력값 검증 실패 (`errors`에 상세) |
| 400 | `INVALID_TRADE_OPTION` | 거래 방법·장소·배송비 조합 오류, 허용되지 않은 거래 방법 |
| 400 | `INVALID_REVIEW_ATTACHMENT` | 첨부할 수 없는 독후감 |
| 400 | `CANNOT_CHAT_OWN_LISTING` | 본인 판매글에 채팅 시작 |
| 400 | `INVALID_CHAT_ROOM` | 이 판매글의 채팅방이 아님 |
| 400 | `EMAIL_VERIFICATION_CODE_EXPIRED` | 인증 코드 만료, 요청한 적 없음, 이미 사용 |
| 400 | `EMAIL_VERIFICATION_CODE_MISMATCH` | 인증 코드 불일치 |
| 400 | `INVALID_SIGNUP_TOKEN` | signupToken 만료, 이미 사용, 존재하지 않음 |
| 401 | `UNAUTHORIZED` | 인증 필요 |
| 401 | `TOKEN_EXPIRED` | Access Token 만료 (프런트는 이 코드로 재발급 시도) |
| 401 | `INVALID_CREDENTIALS` | 로그인 실패 |
| 401 | `INVALID_REFRESH_TOKEN` | 재발급 실패 |
| 403 | `FORBIDDEN` | 권한 없음 |
| 403 | `NOT_CHAT_PARTICIPANT` | 채팅방 참여자가 아님 |
| 404 | `RESOURCE_NOT_FOUND` | 존재하지 않는 경로 |
| 404 | `USER_NOT_FOUND` / `BOOK_NOT_FOUND` / `REVIEW_NOT_FOUND` / `LISTING_NOT_FOUND` / `CHAT_ROOM_NOT_FOUND` / `TRANSACTION_NOT_FOUND` / `BOOKSHELF_ITEM_NOT_FOUND` / `NOTIFICATION_NOT_FOUND` | 리소스 없음 |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP 메서드 |
| 409 | `DUPLICATE_EMAIL` / `DUPLICATE_NICKNAME` | 중복 가입 |
| 409 | `REVIEW_ALREADY_ATTACHED` | 다른 판매글에 첨부된 독후감 |
| 409 | `LISTING_NOT_EDITABLE` | 수정할 수 없는 상태 |
| 409 | `LISTING_RESERVED` | 예약 중이라 삭제 불가 |
| 409 | `LISTING_NOT_AVAILABLE` | 판매 완료·숨김 판매글에 채팅 시작 |
| 409 | `LISTING_ALREADY_RESERVED` | 이미 예약됨 |
| 409 | `TRANSACTION_NOT_CANCELABLE` | 취소할 수 없는 거래 |
| 409 | `TRADE_NOT_COMPLETED` | 완료되지 않은 거래에 후기 작성 |
| 409 | `TRADE_REVIEW_ALREADY_WRITTEN` | 이미 후기를 씀 |
| 409 | `BOOKSHELF_ITEM_EXISTS` | 이미 서재에 담긴 책 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 지원하지 않는 Content-Type |
| 429 | `EMAIL_VERIFICATION_IP_LIMIT` | 같은 IP에서 인증 코드 요청 한도(1시간 20회) 초과 |
| 429 | `EMAIL_VERIFICATION_TOO_FREQUENT` | 인증 코드 재발송 쿨다운(60초) 중 |
| 429 | `EMAIL_VERIFICATION_DAILY_LIMIT` | 인증 코드 하루 발송 한도(10회) 초과 |
| 429 | `EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED` | 인증 코드 입력 5회 초과, 코드 폐기 |
| 429 | `LOGIN_ATTEMPTS_EXCEEDED` | 15분 동안 계정별 또는 IP별 로그인 시도 한도 초과 |
| 500 | `INTERNAL_ERROR` | 서버 오류 |
| 502 | `BOOK_SEARCH_UNAVAILABLE` | 외부 도서 API 오류 |

## 5. 이후 추가할 API

| 단계 | API |
|---|---|
| 소셜 로그인 | 구글·카카오 로그인 (`GET /auth/oauth/{provider}`, 콜백). 소셜 쪽에서 인증된 이메일이면 같은 이메일의 기존 계정과 연결, 카카오에서 이메일을 받지 못하면 이메일 인증 흐름으로 입력받음 |
| 캐시·성능 | 도서 시세 (`GET /books/{id}/price-stats`): 최근 거래가 평균, 정가 대비 비율 |
| 캐시·성능 | 인기 매물·인기 도서 (`GET /rankings/listings`, `GET /rankings/books`): Redis Sorted Set |
| 커뮤니티 | 독후감 공감 (`POST`/`DELETE /reviews/{id}/likes`) |
| 거래 | 판매글 찜 (`POST`/`DELETE /listings/{id}/wishes`) |
| 운영 | 신고 (`POST /reports`), 차단 (`POST`/`DELETE /users/{id}/block`), 관리자 신고 처리 (`/admin/reports`) |
| AI 추천 | 대화형 추천 (`POST /recommendations/chat`, SSE 스트리밍) |
| AI 리포트 | 월간 독서 리포트 (`GET /reports/monthly?yearMonth=2026-11`) |

## 6. 범위에서 제외한 것

- 결제(PG 연동), 배송 추적, 동네 인증·위치 기반 검색

## 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-11-15 | v1 초안 작성 (인증, 회원, 도서, 독후감, 매물, 이미지, 거래, 추천) |
| 2026-11-15 | v2 채팅(REST + STOMP), 알림(SSE), 거래 후기, 내 서재 추가. 거래 방법 선택, 판매자가 채팅 상대 중 예약자 선택 |
| 2026-11-15 | 공통 에러 코드 추가 (`RESOURCE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `UNSUPPORTED_MEDIA_TYPE`) |
| 2026-11-17 | 회원가입 규칙 구체화: 이메일 소문자 정규화, 닉네임 허용 문자, `Location` 헤더, 탈퇴 회원 기준 중복 |
| 2026-11-17 | v2.1 가입 전 이메일 인증 추가(코드 발송·확인 API, 429 상태 코드, 에러 코드 6개). 회원가입 요청을 `email` → `signupToken`으로 변경. 비밀번호를 ASCII로 제한(BCrypt 72바이트), 이메일 앞뒤 공백 제거. 이후 추가할 API에 소셜 로그인 |
| 2026-11-18 | 인증 코드 발송: IP당 요청 제한(1시간 20회, `EMAIL_VERIFICATION_IP_LIMIT`) 추가, 가입 여부 노출(`409 DUPLICATE_EMAIL`)을 감수한 위험으로 명시. 코드 확인은 동시 요청에도 토큰 하나만 발급 |
| 2026-11-20 | 로그인·토큰 재발급·로그아웃 구현 반영: Refresh Token 14일, 재발급 시 교체, 로그아웃은 Access Token 없이도 호출 가능, 로그인 시도 제한(IP 50회·계정 10회/15분, `LOGIN_ATTEMPTS_EXCEEDED`), 72바이트 초과 비밀번호 처리 |
| 2026-11-23 | v2.2 OpenAPI 문서 위치, `TOKEN_EXPIRED` 처리 흐름, 브라우저 호출(CORS·쿠키) 규칙 추가 |
| 2026-11-25 | CORS 프리플라이트 캐시(1시간), 재발급 동시 요청 금지 규칙 추가 |
