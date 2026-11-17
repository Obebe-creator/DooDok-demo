package com.doodok.domain.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/auth/email-verifications/confirm 요청 */
public record EmailVerificationConfirmRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "인증 코드는 필수입니다.")
        @Pattern(regexp = "^\\d{6}$", message = "인증 코드는 숫자 6자리입니다.")
        String code
) {

    public EmailVerificationConfirmRequest {
        email = EmailNormalizer.normalize(email);
    }
}
