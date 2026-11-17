package com.doodok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignupRequestTest {

    @Test
    @DisplayName("이메일은 앞뒤 공백을 지우고 소문자로 바꾼다")
    void normalizeEmail() {
        SignupRequest request = new SignupRequest("  Reader@DooDok.COM ", "Passw0rd!", "책읽는곰");

        assertThat(request.email()).isEqualTo("reader@doodok.com");
    }

    @Test
    @DisplayName("이메일이 null이면 그대로 두어 @NotBlank 검증이 처리하게 한다")
    void nullEmail() {
        SignupRequest request = new SignupRequest(null, "Passw0rd!", "책읽는곰");

        assertThat(request.email()).isNull();
    }
}
