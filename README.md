# 두독 DooDok

> 읽은 책은 다음 독자에게, 감상은 책과 함께.

**두독**은 다 읽은 책을 다음 독자에게 넘기고, 그 책에 남긴 독후감을 함께 전하는 중고책 거래 서비스입니다.
이름은 "두 번째 독자"이면서, 영어로는 **Do + Dok(讀)**, "읽기를 한다"는 뜻을 함께 담았습니다.

## 주요 기능
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
| Database | PostgreSQL + pgvector, Redis |
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
├── docker-compose.yml  # PostgreSQL(pgvector) + Redis
└── .github/            # CI, 이슈·PR 템플릿
```

## 로컬 실행
### 1. 인프라
```bash
cp .env.example .env
docker compose up -d
```

### 2. 백엔드 (http://localhost:8080)
```bash
cd backend
./gradlew bootRun        # Windows: gradlew.bat bootRun
```
헬스체크: `GET http://localhost:8080/actuator/health`

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
