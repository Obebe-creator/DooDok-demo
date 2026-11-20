package com.doodok.domain.auth;

import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenCleanup {

    private final JdbcTemplate jdbc;

    RefreshTokenCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(initialDelay = 1, fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    void deleteExpiredTokens() {
        jdbc.update("DELETE FROM refresh_tokens WHERE expires_at <= CURRENT_TIMESTAMP");
    }
}
