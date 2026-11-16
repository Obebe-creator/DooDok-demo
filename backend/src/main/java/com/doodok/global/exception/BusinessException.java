package com.doodok.global.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 나타내는 예외. 서비스 계층에서 던지고 {@link GlobalExceptionHandler}가 에러 응답으로 바꾼다.
 *
 * <pre>
 * throw new BusinessException(ListingErrorCode.LISTING_ALREADY_RESERVED);
 * </pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 상황에 맞게 사용자 메시지를 바꿔야 할 때 사용한다. */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
