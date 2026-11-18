package com.doodok.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

    @Test
    @DisplayName("이메일은 앞뒤 공백을 지우고 소문자로 바꾼다")
    void normalize() {
        assertThat(EmailNormalizer.normalize("  Reader@DooDok.COM ")).isEqualTo("reader@doodok.com");
    }

    @Test
    @DisplayName("null이면 그대로 두어 @NotBlank 검증이 처리하게 한다")
    void nullStaysNull() {
        assertThat(EmailNormalizer.normalize(null)).isNull();
    }

    @Test
    @DisplayName("요청 DTO는 생성될 때 이메일을 정규화한다")
    void requestDtosNormalize() {
        assertThat(new EmailVerificationRequest(" A@B.COM ").email()).isEqualTo("a@b.com");
        assertThat(new EmailVerificationConfirmRequest(" A@B.COM ", "123456").email()).isEqualTo("a@b.com");
    }
}
