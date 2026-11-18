package com.doodok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doodok.global.exception.BusinessException;
import com.doodok.support.FakeEmailSender;
import com.doodok.support.IntegrationTest;
import com.doodok.support.IntegrationTestHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 이메일 인증 API를 실제 Redis(Testcontainers) 위에서 검증한다. 메일은 {@link FakeEmailSender}가 받는다.
 */
@IntegrationTest
class EmailVerificationApiTest {

    private static final String EMAIL = "reader@doodok.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeEmailSender emailSender;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private IntegrationTestHelper helper;

    @AfterEach
    void tearDown() {
        helper.cleanUp();
    }

    @Test
    @DisplayName("코드 발송 → 확인 → signupToken 발급 → 회원가입까지 이어진다")
    void fullFlow() throws Exception {
        sendCode("Reader@DooDok.com")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.resendAvailableIn").value(60));

        // 정규화(소문자)된 주소로 메일이 간다.
        assertThat(emailSender.sentTo(EMAIL)).hasSize(1);
        String code = emailSender.latestCodeFor(EMAIL);

        String token = confirm(EMAIL, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"signupToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        signup(token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("Redis에는 코드 원문이 아니라 해시가 저장된다")
    void codeStoredAsHash() throws Exception {
        sendCode(EMAIL).andExpect(status().isAccepted());
        String code = emailSender.latestCodeFor(EMAIL);

        String stored = redis.opsForValue().get(EmailVerificationService.CODE_KEY + EMAIL);
        assertThat(stored).isNotNull().isNotEqualTo(code).hasSize(64);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 코드를 보내지 않고 409 DUPLICATE_EMAIL")
    void alreadyRegistered() throws Exception {
        signup(helper.issueSignupToken(EMAIL)).andExpect(status().isCreated());
        redis.delete(EmailVerificationService.COOLDOWN_KEY + EMAIL); // 가입 과정에서 생긴 쿨다운만 지운다
        emailSender.clear();

        sendCode(EMAIL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        assertThat(emailSender.sentTo(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("한 IP에서 이메일을 바꿔가며 1시간에 20번을 넘게 요청하면 429 (가입 여부 대량 조회 방지)")
    void ipLimit() throws Exception {
        for (int i = 0; i < EmailVerificationService.IP_LIMIT; i++) {
            sendCodeFrom("10.0.0.1", "user" + i + "@doodok.com").andExpect(status().isAccepted());
        }

        sendCodeFrom("10.0.0.1", "another@doodok.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_IP_LIMIT"));

        // 다른 IP는 영향을 받지 않는다.
        sendCodeFrom("10.0.0.2", "another@doodok.com").andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("1분 안에 다시 요청하면 429 EMAIL_VERIFICATION_TOO_FREQUENT")
    void resendCooldown() throws Exception {
        sendCode(EMAIL).andExpect(status().isAccepted());

        sendCode(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOO_FREQUENT"));
        assertThat(emailSender.sentTo(EMAIL)).hasSize(1);
    }

    @Test
    @DisplayName("하루 10번을 넘게 요청하면 429 EMAIL_VERIFICATION_DAILY_LIMIT")
    void dailyLimit() throws Exception {
        for (int i = 0; i < EmailVerificationService.DAILY_LIMIT; i++) {
            sendCode(EMAIL).andExpect(status().isAccepted());
            redis.delete(EmailVerificationService.COOLDOWN_KEY + EMAIL); // 쿨다운만 건너뛴다
        }

        sendCode(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_DAILY_LIMIT"));
    }

    @Test
    @DisplayName("IP·일일·틀린 횟수 카운터에는 항상 TTL이 걸린다")
    void countersAlwaysHaveTtl() throws Exception {
        sendCode(EMAIL).andExpect(status().isAccepted());
        confirm(EMAIL, wrongCodeFor(emailSender.latestCodeFor(EMAIL)));

        Long ipTtl = redis.getExpire(EmailVerificationService.IP_KEY + "127.0.0.1", TimeUnit.SECONDS); // MockMvc 기본 IP
        Long dailyTtl = redis.getExpire(EmailVerificationService.DAILY_KEY + EMAIL, TimeUnit.SECONDS);
        Long attemptsTtl = redis.getExpire(EmailVerificationService.ATTEMPTS_KEY + EMAIL, TimeUnit.SECONDS);
        assertThat(ipTtl).isPositive().isLessThanOrEqualTo(EmailVerificationService.IP_WINDOW.toSeconds());
        assertThat(dailyTtl).isPositive().isLessThanOrEqualTo(EmailVerificationService.DAILY_WINDOW.toSeconds());
        assertThat(attemptsTtl).isPositive().isLessThanOrEqualTo(EmailVerificationService.CODE_TTL.toSeconds());
    }

    @Test
    @DisplayName("코드가 틀리면 400 EMAIL_VERIFICATION_CODE_MISMATCH")
    void wrongCode() throws Exception {
        sendCode(EMAIL).andExpect(status().isAccepted());

        confirm(EMAIL, wrongCodeFor(emailSender.latestCodeFor(EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_MISMATCH"));
    }

    @Test
    @DisplayName("5번 틀리면 코드가 폐기되어, 이후에는 맞는 코드도 쓸 수 없다")
    void attemptsExceeded() throws Exception {
        sendCode(EMAIL).andExpect(status().isAccepted());
        String code = emailSender.latestCodeFor(EMAIL);
        String wrong = wrongCodeFor(code);

        for (int i = 0; i < EmailVerificationService.MAX_ATTEMPTS; i++) {
            confirm(EMAIL, wrong).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_MISMATCH"));
        }
        confirm(EMAIL, wrong)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED"));

        confirm(EMAIL, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_EXPIRED"));
    }

    @Test
    @DisplayName("코드가 만료되었거나 요청한 적이 없으면 400 EMAIL_VERIFICATION_CODE_EXPIRED")
    void expiredCode() throws Exception {
        sendCode(EMAIL).andExpect(status().isAccepted());
        String code = emailSender.latestCodeFor(EMAIL);
        redis.delete(EmailVerificationService.CODE_KEY + EMAIL); // 5분이 지나 TTL로 지워진 상황

        confirm(EMAIL, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_EXPIRED"));
    }

    @Test
    @DisplayName("코드는 한 번만 쓸 수 있다")
    void codeIsSingleUse() throws Exception {
        sendCode(EMAIL).andExpect(status().isAccepted());
        String code = emailSender.latestCodeFor(EMAIL);
        confirm(EMAIL, code).andExpect(status().isOk());

        confirm(EMAIL, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_EXPIRED"));
    }

    @Test
    @Timeout(60)
    @DisplayName("같은 코드로 동시에 확인해도 signupToken은 하나만 발급된다")
    void concurrentConfirmIssuesOneToken() throws Exception {
        emailVerificationService.sendCode(EMAIL, "10.0.0.1");
        String code = emailSender.latestCodeFor(EMAIL);
        int threadCount = 5; // 틀린 횟수 제한(5회) 안에서 모두 맞는 코드로 요청한다
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        Queue<SignupTokenResponse> issued = new ConcurrentLinkedQueue<>();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            try {
                for (int i = 0; i < threadCount; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        try {
                            start.await();
                            issued.add(emailVerificationService.confirm(EMAIL, code));
                        } catch (Throwable t) {
                            failures.add(t);
                        }
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(issued).hasSize(1);
        assertThat(failures).hasSize(threadCount - 1);
        assertThat(failures).allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.EMAIL_VERIFICATION_CODE_EXPIRED));
    }

    @Test
    @DisplayName("소문자로 바꾸면 255자를 넘는 이메일은 400으로 막고 메일을 보내지 않는다")
    void emailTooLongAfterNormalization() throws Exception {
        // 'İ'(U+0130)는 소문자로 바꾸면 "i̇"(2글자)가 된다. 원문은 255자, 변환 후 319자.
        String email = "İ".repeat(64) + "@"
                + "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(62);
        assertThat(email).hasSize(255);

        sendCode(email)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));
    }

    @Test
    @DisplayName("코드 형식이 숫자 6자리가 아니면 400 INVALID_INPUT")
    void invalidCodeFormat() throws Exception {
        confirm(EMAIL, "12ab")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"));
    }

    private String wrongCodeFor(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private ResultActions sendCode(String email) throws Exception {
        return sendCodeFrom("127.0.0.1", email);
    }

    private ResultActions sendCodeFrom(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/auth/email-verifications")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s" }
                        """.formatted(email)));
    }

    private ResultActions confirm(String email, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/email-verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s", "code": "%s" }
                        """.formatted(email, code)));
    }

    private ResultActions signup(String signupToken) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "signupToken": "%s", "password": "Passw0rd!", "nickname": "책읽는곰" }
                        """.formatted(signupToken)));
    }
}
