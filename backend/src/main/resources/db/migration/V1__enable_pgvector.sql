-- 임베딩 컬럼(vector 타입)을 쓰기 위해 pgvector 확장을 켠다.
-- 로컬 docker 초기화 스크립트에도 있지만, 배포 DB와 테스트 DB에서도 같은 상태를 보장하려고 마이그레이션에 둔다.
CREATE EXTENSION IF NOT EXISTS vector;
