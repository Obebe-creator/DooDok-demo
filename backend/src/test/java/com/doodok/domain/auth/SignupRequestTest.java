package com.doodok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignupRequestTest {

    @Test
    @DisplayName("toString에는 signupToken과 비밀번호가 드러나지 않는다")
    void toStringMasksSecrets() {
        SignupRequest request = new SignupRequest("secret-token", "Passw0rd!", "책읽는곰");

        assertThat(request.toString())
                .doesNotContain("secret-token")
                .doesNotContain("Passw0rd!")
                .contains("책읽는곰");
    }
}
