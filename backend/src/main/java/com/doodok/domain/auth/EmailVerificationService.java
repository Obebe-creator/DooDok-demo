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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
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
 *
 * <p><b>가입 여부 노출</b>: 이미 가입된 이메일이면 409로 바로 알려준다. (사용자가 화면에서 바로 알 수 있게)
 * 대신 이 API는 로그인 없이 누구나 부를 수 있으므로, 이메일을 바꿔가며 가입 여부를 대량으로 조회하지 못하게
 * IP당 요청 횟수를 제한한다. 이 결정과 이유는 docs/06-api-spec.md에 남겨 두었다.
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final Duration SIGNUP_TOKEN_TTL = Duration.ofMinutes(10);
    static final Duration DAILY_WINDOW = Duration.ofDays(1);
    static final Duration IP_WINDOW = Duration.ofHours(1);
    static final int MAX_ATTEMPTS = 5;
    static final int DAILY_LIMIT = 10;
    static final int IP_LIMIT = 20;

    static final String CODE_KEY = "email-verification:code:";
    static final String ATTEMPTS_KEY = "email-verification:attempts:";
    static final String COOLDOWN_KEY = "email-verification:cooldown:";
    static final String DAILY_KEY = "email-verification:daily:";
    static final String IP_KEY = "email-verification:ip:";
    static final String SIGNUP_TOKEN_KEY = "signup-token:";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 카운터를 1 올리고, 처음 만들어졌을 때만 TTL을 건다. 두 명령을 Lua로 묶어 Redis가 한 번에 실행한다.
     * INCR과 EXPIRE를 따로 보내면 그 사이에 서버가 죽었을 때 TTL 없는 카운터가 남아,
     * 그 이메일의 요청이 영원히 막힐 수 있다.
     */
    private static final RedisScript<Long> INCREMENT_WITH_TTL = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    /**
     * 코드 확인 전체(조회 → 틀린 횟수 증가 → 비교 → 삭제)를 Redis가 한 번에 실행한다.
     * 코드를 Java로 읽어 와서 비교하고 나중에 지우면, 그 사이에 새 코드가 발급됐을 때
     * 예전 코드로 비교에 성공하고 새 코드를 지우는 문제가 생길 수 있다. 스크립트 안에서는 다른 요청이 끼어들 수 없다.
     *
     * KEYS[1]=코드 키, KEYS[2]=틀린 횟수 키 / ARGV[1]=입력 코드의 해시, ARGV[2]=틀린 횟수 TTL(초), ARGV[3]=최대 시도 횟수
     */
    private static final RedisScript<Long> CONFIRM_CODE = RedisScript.of("""
            local saved = redis.call('GET', KEYS[1])
            if not saved then
                return 0
            end
            local attempts = redis.call('INCR', KEYS[2])
            if attempts == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[2])
            end
            if attempts > tonumber(ARGV[3]) then
                redis.call('DEL', KEYS[1])
                return -1
            end
            if saved ~= ARGV[1] then
                return -2
            end
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """, Long.class);

    private static final long CONFIRMED = 1;
    private static final long CODE_NOT_FOUND = 0;
    private static final long ATTEMPTS_EXCEEDED = -1;

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;
    private final EmailSender emailSender;

    /**
     * 인증 코드를 발송한다. email은 정규화된 값이어야 한다.
     *
     * @param clientIp 요청한 클라이언트 IP. IP당 요청 횟수 제한에 쓴다.
     */
    public EmailVerificationResponse sendCode(String email, String clientIp) {
        // IP당 요청 제한: 가입 여부를 응답으로 알려주므로, 한 곳에서 이메일을 바꿔가며 대량으로 조회하는 것을 막는다.
        // 가입 여부를 확인하기 전에 검사해야 제한에 걸린 요청은 가입 여부를 알 수 없다.
        if (incrementWithTtl(IP_KEY + clientIp, IP_WINDOW) > IP_LIMIT) {
            throw new BusinessException(AuthErrorCode.EMAIL_VERIFICATION_IP_LIMIT);
        }

        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }

        // 재발송 쿨다운: 키가 없을 때만 저장(SET NX)되므로, 60초 안의 두 번째 요청은 여기서 막힌다.
        Boolean firstInCooldown = redis.opsForValue().setIfAbsent(COOLDOWN_KEY + email, "1", RESEND_COOLDOWN);
        if (!Boolean.TRUE.equals(firstInCooldown)) {
            throw new BusinessException(AuthErrorCode.EMAIL_VERIFICATION_TOO_FREQUENT);
        }

        // 하루 발송 한도
        if (incrementWithTtl(DAILY_KEY + email, DAILY_WINDOW) > DAILY_LIMIT) {
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
        Long result = redis.execute(CONFIRM_CODE,
                List.of(CODE_KEY + email, ATTEMPTS_KEY + email),
                hash(code), String.valueOf(CODE_TTL.toSeconds()), String.valueOf(MAX_ATTEMPTS));

        if (result == null || result == CODE_NOT_FOUND) {
            // 만료, 요청한 적 없음, 이미 사용(동시에 들어온 다른 요청이 먼저 사용한 경우 포함)
            throw new BusinessException(AuthErrorCode.EMAIL_VERIFICATION_CODE_EXPIRED);
        }
        if (result == ATTEMPTS_EXCEEDED) {
            // 6자리 코드를 무작위로 대입해 맞히는 것을 막는다. 코드는 스크립트에서 이미 폐기했다.
            throw new BusinessException(AuthErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        if (result != CONFIRMED) {
            throw new BusinessException(AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
        }

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

    private long incrementWithTtl(String key, Duration ttl) {
        Long count = redis.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(ttl.toSeconds()));
        return count == null ? 0 : count;
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
