# ADR 0002. 스키마는 Flyway로 관리하고, 통합 테스트는 Testcontainers로 실행한다

- 상태: 확정
- 날짜: 2026-11-16

## 배경
ERD에는 JPA `ddl-auto`로 만들 수 없는 스키마가 있다.
- 거래 이중 예약 방지용 부분 유니크 인덱스 (`WHERE status IN ('RESERVED', 'COMPLETED')`)
- pgvector `vector(1536)` 컬럼과 HNSW 인덱스, `CREATE EXTENSION vector`
- 알림 `payload`의 `jsonb`

기존 테스트는 H2(PostgreSQL 모드)를 썼는데, H2는 위 기능을 지원하지 않는다. 테스트 DB와 운영 DB가 다르게 동작하면 동시성 테스트도 믿을 수 없다.

## 선택지
**스키마 관리**
1. JPA `ddl-auto: update` 유지
2. Flyway (SQL 파일 버전 관리)
3. Liquibase (XML·YAML·SQL 변경 세트, 롤백 자동 생성)

**테스트 DB**
1. H2 유지
2. 로컬 docker-compose DB에 붙여 테스트
3. Testcontainers (테스트마다 컨테이너 생성)

## 결정
Flyway + Testcontainers. 테스트 컨테이너는 운영과 같은 `pgvector/pgvector:pg17` 이미지를 쓴다.

## 이유
- Flyway는 순수 SQL이라 부분 인덱스, 확장, 벡터 인덱스를 그대로 적을 수 있다. 규칙이 "버전 순서대로 한 번씩 실행"뿐이라 1인 프로젝트에 맞다.
- Liquibase의 롤백 자동 생성은 장점이지만, 형식과 개념이 많아 이 프로젝트 규모에는 과하다.
- `ddl-auto: update`는 변경 이력이 남지 않고 컬럼 삭제·인덱스 같은 변경을 반영하지 못한다. 모든 환경에서 `validate`로 두어, 엔티티와 마이그레이션이 어긋나면 시작할 때 바로 실패하게 했다.
- Testcontainers를 쓰면 테스트에서도 Flyway 마이그레이션이 실제로 실행된다. 마이그레이션 SQL 자체가 테스트되고, CI(GitHub Actions ubuntu 러너)에서도 Docker로 바로 돈다.
- docker-compose DB에 붙이는 방식은 테스트 데이터가 로컬 개발 데이터와 섞이고, CI에서 별도 서비스 설정이 필요하다.

## 감수하는 점
- 엔티티를 바꿀 때마다 SQL을 직접 작성해야 한다.
- Flyway 무료판에는 되돌리기(undo)가 없다. 되돌릴 때는 반대 작업을 하는 새 마이그레이션을 추가한다.
- 로컬 테스트에 Docker가 필요하고, 컨테이너를 띄우는 만큼 H2보다 테스트 시작이 느리다.
