package com.doodok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
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
 * 실제 PostgreSQL(Testcontainers) 위에서 회원가입 API 전체 흐름을 검증한다.
 * 동시성 테스트가 있어서 @Transactional 롤백 대신 테스트마다 테이블을 비운다.
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

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("회원가입에 성공하면 201과 Location 헤더를 주고, 비밀번호는 해시로 저장한다")
    void signupSuccess() throws Exception {
        signup("Reader@DooDok.com", "Passw0rd!", "책읽는곰")
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
    @DisplayName("입력 규칙을 어기면 400과 함께 필드별 사유를 알려준다")
    void invalidInput() throws Exception {
        signup("not-an-email", "password", "a")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("email", "password", "nickname")));
    }

    @Test
    @DisplayName("소문자로 바꾸면 255자를 넘는 이메일은 저장 전에 400으로 막는다")
    void emailTooLongAfterNormalization() throws Exception {
        // 'İ'(U+0130)는 소문자로 바꾸면 "i̇"(2글자)가 된다. 원문은 255자, 변환 후 319자.
        String email = "\u0130".repeat(64) + "@"
                + "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(62);
        assertThat(email).hasSize(255);

        signup(email, "Passw0rd!", "책읽는곰")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("ASCII가 아닌 문자가 들어간 비밀번호는 400으로 막는다 (BCrypt 72바이트 제한 대비)")
    void nonAsciiPasswordRejected() throws Exception {
        // 20글자지만 UTF-8로는 74바이트. 허용하면 BCrypt가 예외를 던져 500이 된다.
        String emojiPassword = "a1" + "\uD83D\uDE00".repeat(18);

        signup("reader@doodok.com", emojiPassword, "책읽는곰")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        signup("reader@doodok.com", "비밀번호abc1!", "책읽는곰")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("대소문자만 다른 같은 이메일로 가입하면 409 DUPLICATE_EMAIL")
    void duplicateEmailIgnoringCase() throws Exception {
        signup("reader@doodok.com", "Passw0rd!", "책읽는곰").andExpect(status().isCreated());

        signup("READER@doodok.com", "Passw0rd!", "다른닉네임")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임으로 가입하면 409 DUPLICATE_NICKNAME")
    void duplicateNickname() throws Exception {
        signup("reader@doodok.com", "Passw0rd!", "책읽는곰").andExpect(status().isCreated());

        signup("other@doodok.com", "Passw0rd!", "책읽는곰")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
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
    @DisplayName("같은 이메일로 동시에 가입해도 한 명만 성공하고 나머지는 DUPLICATE_EMAIL")
    void concurrentSignupWithSameEmail() throws Exception {
        int threadCount = 10;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();

        // try-with-resources: 블록을 어떻게 빠져나가든(성공, 시간 초과, 예외) close()가
        // 남은 작업이 모두 끝날 때까지 기다린 뒤 스레드를 정리한다.
        // 그래서 @AfterEach의 테이블 삭제가 아직 실행 중인 가입 작업과 겹치지 않는다.
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            try {
                for (int i = 0; i < threadCount; i++) {
                    String nickname = "동시가입" + i;
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        try {
                            start.await(); // 모든 스레드가 준비되면 한꺼번에 출발
                            authService.signup(new SignupRequest("same@doodok.com", "Passw0rd!", nickname));
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

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(failures).hasSize(threadCount - 1);
        assertThat(failures).allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.DUPLICATE_EMAIL));
    }

    private ResultActions signup(String email, String password, String nickname) throws Exception {
        String body = """
                { "email": "%s", "password": "%s", "nickname": "%s" }
                """.formatted(email, password, nickname);
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
