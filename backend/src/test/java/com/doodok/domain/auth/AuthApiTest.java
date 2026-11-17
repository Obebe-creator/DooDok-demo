package com.doodok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doodok.domain.user.User;
import com.doodok.domain.user.UserErrorCode;
import com.doodok.domain.user.UserRepository;
import com.doodok.global.exception.BusinessException;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 실제 PostgreSQL·Redis(Testcontainers) 위에서 회원가입 API 전체 흐름을 검증한다.
 * 이메일 인증은 {@link IntegrationTestHelper#issueSignupToken}으로 끝낸 뒤 가입한다.
 * 동시성 테스트가 있어서 @Transactional 롤백 대신 테스트마다 데이터를 비운다.
 */
@IntegrationTest
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IntegrationTestHelper helper;

    @AfterEach
    void tearDown() {
        helper.cleanUp();
    }

    @Test
    @DisplayName("인증을 마친 이메일로 가입하면 201과 Location 헤더를 주고, 비밀번호는 해시로 저장한다")
    void signupSuccess() throws Exception {
        String token = helper.issueSignupToken("reader@doodok.com");

        signup(token, "Passw0rd!", "책읽는곰")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/users/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("reader@doodok.com"))
                .andExpect(jsonPath("$.nickname").value("책읽는곰"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findAll().getFirst();
        assertThat(saved.getPasswordHash()).isNotEqualTo("Passw0rd!").startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("Passw0rd!", saved.getPasswordHash())).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("signupToken 없이, 또는 잘못된 토큰으로 가입하면 막는다")
    void signupWithoutValidToken() throws Exception {
        signup("", "Passw0rd!", "책읽는곰")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("signupToken"));

        signup("not-issued-token", "Passw0rd!", "책읽는곰")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNUP_TOKEN"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("signupToken은 한 번만 쓸 수 있다")
    void signupTokenIsSingleUse() throws Exception {
        String token = helper.issueSignupToken("reader@doodok.com");
        signup(token, "Passw0rd!", "책읽는곰").andExpect(status().isCreated());

        signup(token, "Passw0rd!", "다른닉네임")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNUP_TOKEN"));
    }

    @Test
    @DisplayName("닉네임 중복으로 실패해도 같은 토큰으로 다른 닉네임을 써서 다시 가입할 수 있다")
    void retryWithSameTokenAfterNicknameConflict() throws Exception {
        signup(helper.issueSignupToken("first@doodok.com"), "Passw0rd!", "책읽는곰").andExpect(status().isCreated());
        String token = helper.issueSignupToken("second@doodok.com");

        signup(token, "Passw0rd!", "책읽는곰")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));

        signup(token, "Passw0rd!", "다른닉네임").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("입력 규칙을 어기면 400과 함께 필드별 사유를 알려준다")
    void invalidInput() throws Exception {
        String token = helper.issueSignupToken("reader@doodok.com");

        signup(token, "password", "a")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("password", "nickname")));
    }

    @Test
    @DisplayName("ASCII가 아닌 문자가 들어간 비밀번호는 400으로 막는다 (BCrypt 72바이트 제한 대비)")
    void nonAsciiPasswordRejected() throws Exception {
        String token = helper.issueSignupToken("reader@doodok.com");
        // 20글자지만 UTF-8로는 74바이트. 허용하면 BCrypt가 예외를 던져 500이 된다.
        String emojiPassword = "a1" + "😀".repeat(18);

        signup(token, emojiPassword, "책읽는곰")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        signup(token, "비밀번호abc1!", "책읽는곰")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("로그인이 필요한 경로에 토큰 없이 요청하면 공통 형식의 401로 응답한다")
    void unauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    @Timeout(60) // 작업이 멈추더라도 테스트 전체가 무한정 대기하지 않게
    @DisplayName("같은 signupToken으로 동시에 가입해도 한 명만 성공한다")
    void concurrentSignupWithSameToken() throws Exception {
        String token = helper.issueSignupToken("same@doodok.com");
        int threadCount = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();

        // try-with-resources: 블록을 어떻게 빠져나가든(성공, 시간 초과, 예외) close()가
        // 남은 작업이 모두 끝날 때까지 기다린 뒤 스레드를 정리한다.
        // 그래서 @AfterEach의 데이터 삭제가 아직 실행 중인 가입 작업과 겹치지 않는다.
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            try {
                for (int i = 0; i < threadCount; i++) {
                    String nickname = "동시가입" + i;
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        try {
                            start.await(); // 모든 스레드가 준비되면 한꺼번에 출발
                            authService.signup(new SignupRequest(token, "Passw0rd!", nickname));
                        } catch (Throwable t) {
                            failures.add(t);
                        }
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                // 준비 단계에서 실패해도 start를 기다리는 스레드가 풀려나야 close()가 끝날 수 있다.
                start.countDown();
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        // 동시에 들어온 요청은 DB 유니크 인덱스가 막고(DUPLICATE_EMAIL),
        // 첫 가입이 끝나 토큰이 지워진 뒤 들어온 요청은 토큰 검사에서 막힌다(INVALID_SIGNUP_TOKEN).
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(failures).hasSize(threadCount - 1);
        assertThat(failures).allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isIn(UserErrorCode.DUPLICATE_EMAIL, AuthErrorCode.INVALID_SIGNUP_TOKEN));
    }

    private ResultActions signup(String signupToken, String password, String nickname) throws Exception {
        String body = """
                { "signupToken": "%s", "password": "%s", "nickname": "%s" }
                """.formatted(signupToken, password, nickname);
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
