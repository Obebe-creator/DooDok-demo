package com.doodok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doodok.domain.user.User;
import com.doodok.domain.user.UserRepository;
import com.doodok.support.IntegrationTest;
import com.doodok.support.IntegrationTestHelper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
class LoginApiTest {

    private static final String EMAIL = "reader@doodok.com";
    private static final String PASSWORD = "Passw0rd!";
    private static final String NICKNAME = "책읽는곰";
    private static final String FIRST_IP = "192.0.2.1";
    private static final String SECOND_IP = "192.0.2.2";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private IntegrationTestHelper helper;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RefreshTokenCleanup cleanup;

    @Value("${doodok.auth.jwt-secret}")
    private String jwtSecret;

    @AfterEach
    void tearDown() {
        helper.cleanUp();
    }

    @Test
    void loginIssuesTokensAndAccessTokenIdentifiesCurrentUser() throws Exception {
        User user = saveUser();
        MockHttpServletResponse response = login(" READER@DooDok.com ", PASSWORD, FIRST_IP)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andReturn().getResponse();

        Cookie cookie = refreshCookie(response);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge()).isEqualTo((int) LoginService.REFRESH_TTL.toSeconds());
        String accessToken = json.readTree(response.getContentAsString()).get("accessToken").asString();
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nickname").value(NICKNAME));
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnSameError() throws Exception {
        saveUser();
        MockHttpServletResponse response = login(EMAIL, "wrong", FIRST_IP)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse();
        login("unknown@doodok.com", "wrong", FIRST_IP)
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(response.getContentAsString()));
    }

    @Test
    void refreshRotatesCookieAndRejectsReusedToken() throws Exception {
        saveUser();
        Cookie original = loginCookie();
        Cookie rotated = refreshCookie(refresh(original).andExpect(status().isOk()).andReturn().getResponse());

        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());
        refresh(original).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(rotated).andExpect(status().isOk());
    }

    @Test
    void onlyOneConcurrentRefreshCanConsumeTheSameCookie() throws Exception {
        saveUser();
        Cookie original = loginCookie();
        CyclicBarrier ready = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var requests = List.of(executor.submit(() -> refreshAfterBarrier(original.getValue(), ready)),
                    executor.submit(() -> refreshAfterBarrier(original.getValue(), ready)));
            var first = requests.getFirst().get(10, TimeUnit.SECONDS);
            var second = requests.getLast().get(10, TimeUnit.SECONDS);

            assertThat(List.of(first.getStatus(), second.getStatus())).containsExactlyInAnyOrder(200, 401);
            MockHttpServletResponse successful = first.getStatus() == 200 ? first : second;
            refresh(refreshCookie(successful)).andExpect(status().isOk());
        }
    }

    @Test
    void logoutWithExpiredBearerRevokesCookieAndIsIdempotent() throws Exception {
        User user = saveUser();
        Cookie original = loginCookie();
        SignedJWT expired = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder().issuer("doodok").subject(user.getId().toString())
                        .expirationTime(Date.from(Instant.now().minusSeconds(60))).build());
        expired.sign(new MACSigner(Base64.getDecoder().decode(jwtSecret)));

        mvc.perform(post("/api/auth/logout").cookie(original)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired.serialize()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0));
        refresh(original).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").cookie(original))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0));
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refreshToken", 0));
    }

    @Test
    void accountLimitCountsSuccessfulAndFailedAttemptsAcrossIps() throws Exception {
        saveUser();
        login(EMAIL, PASSWORD, FIRST_IP).andExpect(status().isOk());
        for (int attempt = 0; attempt < 9; attempt++) {
            login(EMAIL, "wrong", FIRST_IP).andExpect(status().isUnauthorized());
        }
        login("READER@DOODOK.COM", PASSWORD, SECOND_IP).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void ipLimitCountsAcrossAccountsAndDoesNotAffectOtherIps() throws Exception {
        saveUser();
        for (int attempt = 0; attempt < 50; attempt++) {
            login("reader" + attempt + "@doodok.com", "a".repeat(73), FIRST_IP)
                    .andExpect(status().isUnauthorized());
        }
        login(EMAIL, PASSWORD, FIRST_IP).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_ATTEMPTS_EXCEEDED"));
        login(EMAIL, PASSWORD, SECOND_IP).andExpect(status().isOk());
    }

    @Test
    void cleanupRemovesOnlyExpiredTokens() throws Exception {
        saveUser();
        Cookie expired = loginCookie();
        jdbc.update("UPDATE refresh_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        Cookie valid = loginCookie();

        cleanup.deleteExpiredTokens();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Long.class)).isEqualTo(1L);
        refresh(expired).andExpect(status().isUnauthorized());
        refresh(valid).andExpect(status().isOk());
    }

    private User saveUser() {
        return users.save(User.create(EMAIL, passwords.encode(PASSWORD), NICKNAME));
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginController.LoginRequest(email, password)))
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }));
    }

    private Cookie loginCookie() throws Exception {
        return refreshCookie(login(EMAIL, PASSWORD, FIRST_IP).andExpect(status().isOk()).andReturn().getResponse());
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(cookie));
    }

    private MockHttpServletResponse refreshAfterBarrier(String token, CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        return refresh(new Cookie("refreshToken", token)).andReturn().getResponse();
    }

    private static Cookie refreshCookie(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie("refreshToken");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
