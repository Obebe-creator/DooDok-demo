package com.doodok.global.exception;

import java.util.List;

/**
 * 모든 API의 에러 응답 형식. (docs/06-api-spec.md 1. 공통 규칙 - 에러 응답)
 *
 * <pre>
 * { "code": "INVALID_INPUT", "message": "입력값이 올바르지 않습니다.", "errors": [ { "field": "price", "reason": "..." } ] }
 * </pre>
 */
public record ErrorResponse(String code, String message, List<FieldErrorDetail> errors) {

    public record FieldErrorDetail(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> errors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.copyOf(errors));
    }
}
