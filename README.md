# 두독 DooDok

> 읽은 책은 다음 독자에게, 감상은 책과 함께.

**두독**은 다 읽은 책을 다음 독자에게 넘기고, 그 책에 남긴 독후감을 함께 전하는 중고책 거래 서비스입니다.
이름은 "두 번째 독자"이면서, 영어로는 **Do + Dok(讀)**, "읽기를 한다"는 뜻을 함께 담았습니다.

## 주요 기능
- **회원가입**: 가입 전 이메일 인증(6자리 코드), 이후 구글·카카오 로그인 지원 예정
- **중고책 거래**: ISBN 검색으로 도서 정보를 자동 입력하고, 판매글에 내 독후감을 첨부
- **독후감**: 공개 여부와 스포일러 표시를 지원하는 독후감 작성·조회
- **독후감 기반 도서 추천**: 독후감 임베딩과 거래 데이터를 합친 하이브리드 추천, 판매 중인 매물로 연결
- **대화형 추천**: LLM이 도서 검색·매물 조회 함수를 호출해 실제 매물 안에서만 추천
- **월간 독서 리포트**: 통계는 SQL로 계산하고 LLM은 해석 문장만 생성

## 기술 스택
| 영역 | 기술 |
|---|---|
| Frontend | Next.js (App Router), TypeScript, Tailwind CSS |
| Backend | Java 21, Spring Boot 4, Spring Security, Spring Data JPA |
| Database | PostgreSQL + pgvector (Flyway 마이그레이션), Redis |
| Mail | Spring Mail, Mailpit(로컬) / AWS SES(운영 예정) |
| AI | Spring AI, Gemini (임베딩, LLM) |
| Infra | Docker, GitHub Actions, AWS EC2·S3 |

선택 이유는 [docs/02-tech-stack.md](docs/02-tech-stack.md)에 정리했습니다.

## 저장소 구조
```
DooDok-demo/
├── backend/            # Spring Boot API 서버
├── frontend/           # Next.js 웹
├── docs/               # 기능 명세, 기술 선택, 로드맵, ERD, ADR
├── docker/             # 로컬 인프라 초기화 스크립트
├── docker-compose.yml  # PostgreSQL(pgvector) + Redis + Mailpit
└── .github/            # CI, 이슈·PR 템플릿
```

## 로컬 실행
### 1. 인프라
```bash
cp .env.example .env
docker compose up -d
```
| 서비스 | 주소 | 용도 |
|---|---|---|
| PostgreSQL | `localhost:5432` | DB (테이블은 앱 시작 시 Flyway가 생성) |
| Redis | `localhost:6379` | 이메일 인증 코드·토큰, 요청 횟수 제한 |
| Mailpit | SMTP `localhost:1025` / 웹 http://localhost:8025 | 로컬 가짜 메일 서버. 인증 메일을 여기서 확인 |

포트는 `127.0.0.1`에만 열린다. (같은 네트워크의 다른 기기에서 접근 불가)

### 2. 백엔드 (http://localhost:8080)
```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
# Windows: gradlew.bat bootRun --args="--spring.profiles.active=local"
```
헬스체크: `GET http://localhost:8080/actuator/health`

`local` 프로필에서는 [OpenAPI JSON](http://localhost:8080/v3/api-docs)과 [Swagger UI](http://localhost:8080/swagger-ui.html)를 사용할 수 있다. 다른 프로필에서는 문서가 기본 비활성화되며, `API_DOCS_ENABLED`로 두 경로를 함께 제어한다.

브라우저는 Spring API를 직접 호출한다. `local` 프로필의 기본 허용 Origin은 `http://localhost:3000`이다. 로컬 브라우저의 프런트와 API 주소에는 모두 `localhost`를 사용한다. 배포할 때는 `CORS_ALLOWED_ORIGINS=https://doodok.xyz`처럼 프런트 Origin을 설정한다. 여러 Origin은 쉼표로 구분한다. 인증 호출에서 Refresh 쿠키를 수신하고 보내려면 `credentials: 'include'`를 사용한다.

테스트 (Docker 실행 필요, Testcontainers가 PostgreSQL·Redis를 띄움):
```bash
./gradlew test
```

배포할 때 모든 백엔드 인스턴스에 동일한 `JWT_SECRET`을 주입한다. `openssl rand -base64 32`로 키를 만들 수 있으며, 누락되거나 올바른 Base64 키가 아니면 시작에 실패한다. 활성 프로필이 `local` 또는 `test`로만 구성되면 빈 키 대신 실행마다 임의 키를 만들 수 있다.
리프레시 쿠키는 기본적으로 `Secure`이며, 로컬 HTTP 테스트에서만 필요에 따라 `AUTH_SECURE_COOKIE=false`로 설정한다.

리버스 프록시 뒤에서는 `FORWARD_HEADERS_STRATEGY=native`와 `TRUSTED_PROXY_PATTERN`을 함께 설정한다. 정규식은 백엔드에 연결하는 신뢰된 프록시 주소만 일치시킨다. 신뢰 경계의 첫 프록시는 외부 요청의 `X-Forwarded-For`를 실제 연결 주소로 덮어쓰고, 백엔드는 프록시를 통해서만 접근하도록 구성한다. 배포 후 서로 다른 클라이언트가 각자 IP 한도로 집계되는지 확인한다.

### 3. 프런트엔드 (http://localhost:3000)
```bash
cd frontend
npm install
npm run dev
```

## 문서
- [기능 명세](docs/01-feature-spec.md)
- [기술 스택과 선택 이유](docs/02-tech-stack.md)
- [로드맵](docs/03-roadmap.md)
- [ERD 설계](docs/04-erd.md)
- [API 명세](docs/06-api-spec.md)
- [개발 컨벤션](docs/05-conventions.md)
- [의사결정 기록 (ADR)](docs/adr)
