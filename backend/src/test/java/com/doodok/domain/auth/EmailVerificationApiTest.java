package com.doodok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doodok.support.FakeEmailSender;
import com.doodok.support.IntegrationTest;
import com.doodok.support.IntegrationTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "signupToken": "%s", "password": "Passw0rd!", "nickname": "책읽는곰" }
                                """.formatted(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("Redis에는 코드 원문이 아니라 해시가 저장된다")
    void codeStoredAsHash() throws Exception {
        sendCode(EMAIL).andExpect(status().isAccepted());
        String code = emailSender.latestCodeFor(EMAIL);

        String stored = redis.opsForValue().get("email-verification:code:" + EMAIL);
        assertThat(stored).isNotNull().isNotEqualTo(code).hasSize(64);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 코드를 보내지 않고 409 DUPLICATE_EMAIL")
    void alreadyRegistered() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "signupToken": "%s", "password": "Passw0rd!", "nickname": "책읽는곰" }
                                """.formatted(helper.issueSignupToken(EMAIL))))
                .andExpect(status().isCreated());
        redis.delete("email-verification:cooldown:" + EMAIL);
        emailSender.clear();

        sendCode(EMAIL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        assertThat(emailSender.sentTo(EMAIL)).isEmpty();
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
            redis.delete("email-verification:cooldown:" + EMAIL); // 쿨다운만 건너뛴다
        }

        sendCode(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_DAILY_LIMIT"));
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
        redis.delete("email-verification:code:" + EMAIL); // 5분이 지나 TTL로 지워진 상황

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
        return mockMvc.perform(post("/api/auth/email-verifications")
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
}
