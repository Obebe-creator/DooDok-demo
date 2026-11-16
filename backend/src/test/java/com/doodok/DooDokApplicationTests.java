package com.doodok;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DooDokApplicationTests {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("앱이 시작되면 Flyway 마이그레이션이 모두 적용된다")
    void flywayMigrationsApplied() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).isNotEmpty();
    }

    @Test
    @DisplayName("pgvector 확장이 켜져 있다")
    void pgvectorExtensionEnabled() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
