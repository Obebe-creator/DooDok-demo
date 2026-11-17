package com.doodok.domain.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/auth/signup 요청. 규칙은 docs/06-api-spec.md 3-1 참고 */
public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S{8,20}$",
                message = "비밀번호는 공백 없이 8~20자이며, 영문·숫자·특수문자를 모두 포함해야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Pattern(
                regexp = "^[가-힣a-zA-Z0-9_]{2,12}$",
                message = "닉네임은 2~12자의 한글, 영문, 숫자, 밑줄(_)만 사용할 수 있습니다.")
        String nickname
) {

    /** 비밀번호가 로그나 디버거에 그대로 찍히지 않도록 가린다. */
    @Override
    public String toString() {
        return "SignupRequest[email=" + email + ", password=****, nickname=" + nickname + "]";
    }
}
