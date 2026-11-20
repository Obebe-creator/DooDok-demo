package com.doodok.domain.auth;

import com.doodok.domain.user.User;
import com.doodok.domain.user.UserRepository;
import com.doodok.global.exception.BusinessException;
import com.doodok.global.security.AccessTokens;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

    static final Duration REFRESH_TTL = Duration.ofDays(14);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> COUNT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], 900) end
            return count
            """, Long.class);

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AccessTokens accessTokens;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder passwords, AccessTokens accessTokens,
                        JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.users = users;
        this.passwords = passwords;
        this.accessTokens = accessTokens;
        this.jdbc = jdbc;
        this.redis = redis;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Session login(String email, String password, String ip) {
        checkLimit("ip", ip, 50);
        String normalized = EmailNormalizer.normalize(email);
        if (normalized == null || normalized.isBlank() || normalized.length() > 255) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        checkLimit("account", normalized, 10);
        if (password == null || password.isEmpty() || password.length() > 72
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        User user = users.findByEmailAndDeletedAtIsNull(normalized).orElse(null);
        boolean matches = passwords.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (!matches || user == null) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        return issue(user);
    }

    @Transactional
    public Session refresh(String token) {
        if (!validTokenFormat(token)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        List<Long> ids = jdbc.queryForList("""
                DELETE FROM refresh_tokens WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP
                RETURNING user_id
                """, Long.class, hash(token));
        if (ids.isEmpty()) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        User user = users.findByIdAndDeletedAtIsNull(ids.getFirst())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        return issue(user);
    }

    public void logout(String token) {
        if (validTokenFormat(token)) {
            jdbc.update("DELETE FROM refresh_tokens WHERE token_hash = ?", hash(token));
        }
    }

    private Session issue(User user) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                user.getId(), hash(token), Timestamp.from(Instant.now().plus(REFRESH_TTL)));
        return new Session(token, new TokenResponse(accessTokens.issue(user.getId()),
                AccessTokens.TTL.toSeconds(), new LoginUser(user.getId(), user.getNickname())));
    }

    private void checkLimit(String type, String key, int limit) {
        Long count = redis.execute(COUNT, List.of("auth:login:" + type + ":" + hash(key)));
        if (count == null || count > limit) {
            throw new BusinessException(AuthErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
        }
    }

    private static boolean validTokenFormat(String token) {
        return token != null && token.length() == 43 && token.matches("[A-Za-z0-9_-]+");
    }

    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Session(String refreshToken, TokenResponse response) {
    }

    public record TokenResponse(String accessToken, long expiresIn, LoginUser user) {
    }

    public record LoginUser(Long id, String nickname) {
    }
}
