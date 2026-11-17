package com.doodok.domain.auth;

import com.doodok.domain.user.UserErrorCode;
import com.doodok.domain.user.UserRepository;
import com.doodok.global.exception.BusinessException;
import com.doodok.global.mail.EmailSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 가입 전 이메일 인증.
 *
 * <pre>
 * 1. sendCode     : 6자리 코드를 만들어 메일로 보내고, 코드의 해시를 Redis에 5분간 저장
 * 2. confirm      : 코드가 맞으면 signupToken을 발급해 Redis에 10분간 저장 (토큰 → 인증된 이메일)
 * 3. 회원가입      : signupToken으로 인증된 이메일을 꺼내 쓰고, 가입이 끝나면 토큰을 지운다
 * </pre>
 *
 * 인증 데이터는 몇 분만 필요한 값이라 DB 대신 Redis에 TTL로 저장한다. 만료되면 Redis가 알아서 지운다.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final Duration SIGNUP_TOKEN_TTL = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    static final int DAILY_LIMIT = 10;

    private static final String CODE_KEY = "email-verification:code:";
    private static final String ATTEMPTS_KEY = "email-verification:attempts:";
    private static final String COOLDOWN_KEY = "email-verification:cooldown:";
    private static final String DAILY_KEY = "email-verification:daily:";
    private static final String SIGNUP_TOKEN_KEY = "signup-token:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;
    private final EmailSender emailSender;

    /** 인증 코드를 발송한다. email은 정규화된 값이어야 한다. */
    public EmailVerificationResponse sendCode(String email) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }

        // 재발송 쿨다운: 키가 없을 때만 저장(SET NX)되므로, 60초 안의 두 번째 요청은 여기서 막힌다.
        Boolean firstInCooldown = redis.opsForValue().setIfAbsent(COOLDOWN_KEY + email, "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(firstInCooldown)) {
            throw new BusinessException(AuthErrorCode.EMAIL_VERIFICATION_TOO_FREQUENT);
        }

        // 하루 발송 한도: 첫 요청 때 24시간 TTL을 걸고 요청마다 1씩 늘린다.
        Long sentToday = redis.opsForValue().increment(DAILY_KEY + email);
        if (sentToday != null && sentToday == 1) {
            redis.expire(DAILY_KEY + email, Duration.ofDays(1));
        }
        if (sentToday != null && sentToday > DAILY_LIMIT) {
            throw new BusinessException(AuthErrorCode.EMAIL_VERIFICATION_DAILY_LIMIT);
        }

        String code = generateCode();
        // 새 코드를 발급하면 이전 코드와 틀린 횟수는 무효가 된다.
        redis.opsForValue().set(CODE_KEY + email, hash(code), CODE_TTL);
        redis.delete(ATTEMPTS_KEY + email);

        emailSender.send(email, "[두독] 이메일 인증 코드", """
                두독 회원가입 인증 코드입니다.

                인증 코드: %s

                %d분 안에 입력해 주세요. 본인이 요청하지 않았다면 이 메일을 무시하세요.
                """.formatted(code, CODE_TTL.toMinutes()));

        return new EmailVerificationResponse(CODE_TTL.toSeconds(), RESEND_COOLDOWN.toSeconds());
    }

    /** 코드가 맞으면 signupToken을 발급한다. email은 정규화된 값이어야 한다. */
    public SignupTokenResponse confirm(String email, String code) {
        String savedHash = redis.opsForValue().get(CODE_KEY + email);
        if (savedHash == null) {
            throw new BusinessException(AuthErrorCode.EMAIL_VERIFICATION_CODE_EXPIRED);
        }

        // 틀린 횟수 제한: 6자리 코드를 무작위로 대입해 맞히는 것을 막는다.
        Long attempts = redis.opsForValue().increment(ATTEMPTS_KEY + email);
        if (attempts != null && attempts == 1) {
            redis.expire(ATTEMPTS_KEY + email, CODE_TTL);
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redis.delete(CODE_KEY + email); // 코드를 폐기해 새로 요청하게 한다.
            throw new BusinessException(AuthErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
        }

        if (!MessageDigest.isEqual(savedHash.getBytes(StandardCharsets.UTF_8), hash(code).getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
        }

        // 코드는 한 번만 쓸 수 있다.
        redis.delete(CODE_KEY + email);
        redis.delete(ATTEMPTS_KEY + email);

        String signupToken = generateToken();
        redis.opsForValue().set(SIGNUP_TOKEN_KEY + signupToken, email, SIGNUP_TOKEN_TTL);
        return new SignupTokenResponse(signupToken, SIGNUP_TOKEN_TTL.toSeconds());
    }

    /**
     * signupToken에 연결된 인증된 이메일을 돌려준다. 토큰은 지우지 않는다.
     * 닉네임 중복처럼 가입이 실패해도 다시 인증하지 않고 재시도할 수 있게, 가입이 끝난 뒤 {@link #consumeSignupToken}으로 지운다.
     */
    public String getVerifiedEmail(String signupToken) {
        String email = redis.opsForValue().get(SIGNUP_TOKEN_KEY + signupToken);
        if (email == null) {
            throw new BusinessException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
        }
        return email;
    }

    public void consumeSignupToken(String signupToken) {
        redis.delete(SIGNUP_TOKEN_KEY + signupToken);
    }

    private String generateCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    /** 추측할 수 없는 256비트 토큰 (URL에 써도 안전한 Base64) */
    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Redis가 노출돼도 코드를 그대로 읽을 수 없도록 해시로 저장한다. */
    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
