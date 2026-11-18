package com.doodok.domain.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/auth/signup 요청. 규칙은 docs/06-api-spec.md 3-1 참고
 *
 * <p>이메일은 받지 않는다. 이메일 인증을 마치고 받은 signupToken에서 인증된 이메일을 꺼내 쓴다.
 * (인증은 A 메일로 받고 가입은 B 메일로 하는 우회를 막기 위해)
 */
public record SignupRequest(

        @NotBlank(message = "이메일 인증이 필요합니다.")
        String signupToken,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(
                // ASCII(0x21~0x7E: 영문, 숫자, 키보드 특수문자)만 허용한다.
                // 최대 20바이트라 BCrypt의 72바이트 제한을 넘지 않는다. (이모지·한글은 한 글자가 여러 바이트)
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!-/:-@\\[-`{-~])[!-~]{8,20}$",
                message = "비밀번호는 8~20자의 영문, 숫자, 특수문자(키보드 기호)로 이루어져야 하며, 세 가지를 모두 포함해야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Pattern(
                regexp = "^[가-힣a-zA-Z0-9_]{2,12}$",
                message = "닉네임은 2~12자의 한글, 영문, 숫자, 밑줄(_)만 사용할 수 있습니다.")
        String nickname
) {

    /** 비밀번호와 토큰이 로그나 디버거에 그대로 찍히지 않도록 가린다. */
    @Override
    public String toString() {
        return "SignupRequest[signupToken=****, password=****, nickname=" + nickname + "]";
    }
}
