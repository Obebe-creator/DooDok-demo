package com.doodok.domain.auth;

/**
 * 인증 코드 발송 응답.
 *
 * @param expiresIn         코드 유효 시간(초)
 * @param resendAvailableIn 다시 요청할 수 있을 때까지 남은 시간(초)
 */
public record EmailVerificationResponse(long expiresIn, long resendAvailableIn) {
}
