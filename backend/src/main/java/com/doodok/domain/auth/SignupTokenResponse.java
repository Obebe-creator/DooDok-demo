package com.doodok.domain.auth;

/**
 * 인증 코드 확인 응답. signupToken을 회원가입 요청에 담아 보낸다.
 *
 * @param expiresIn signupToken 유효 시간(초)
 */
public record SignupTokenResponse(String signupToken, long expiresIn) {
}
