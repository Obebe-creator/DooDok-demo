package com.doodok;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 PostgreSQL 컨테이너.
 * 운영과 같은 pgvector 이미지를 써서 부분 인덱스, jsonb, vector 타입이 실제와 똑같이 동작한다.
 * {@code @Import(TestcontainersConfiguration.class)}로 가져다 쓴다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(PGVECTOR_IMAGE);
    }
}
