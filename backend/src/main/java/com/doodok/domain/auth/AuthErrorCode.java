package com.doodok.domain.auth;

import com.doodok.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    EMAIL_VERIFICATION_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "인증 코드는 1분에 한 번만 요청할 수 있습니다."),
    EMAIL_VERIFICATION_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 인증 코드 요청 횟수를 모두 사용했습니다. 내일 다시 시도해 주세요."),
    EMAIL_VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다. 코드를 다시 요청해 주세요."),
    EMAIL_VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
    EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증 코드 입력 횟수를 초과했습니다. 코드를 다시 요청해 주세요."),
    INVALID_SIGNUP_TOKEN(HttpStatus.BAD_REQUEST, "이메일 인증이 만료되었거나 올바르지 않습니다. 다시 인증해 주세요.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
