package com.doodok.domain.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * POST /api/auth/signup 요청. 규칙은 docs/06-api-spec.md 3-1 참고
 *
 * <p>이메일은 생성자에서 정규화(앞뒤 공백 제거, 소문자 변환)한다.
 * 검증 어노테이션은 객체가 만들어진 뒤에 실행되므로, 길이·형식 검사가 정규화된 값(= 실제로 저장될 값)에 적용된다.
 * 소문자 변환으로 길이가 늘어나는 문자(예: 'İ' → "i̇")가 있어도 255자 제한을 넘는 값은 저장 전에 400으로 걸러진다.
 */
public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

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

    public SignupRequest {
        email = normalizeEmail(email);
    }

    /** 대소문자만 다른 이메일로 중복 가입하는 것을 막기 위해 소문자로 맞춘다. */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** 비밀번호가 로그나 디버거에 그대로 찍히지 않도록 가린다. */
    @Override
    public String toString() {
        return "SignupRequest[email=" + email + ", password=****, nickname=" + nickname + "]";
    }
}
