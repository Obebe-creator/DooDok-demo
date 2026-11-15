package com.doodok.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 공통 규약.
 * 공통 에러는 {@link CommonErrorCode}, 도메인별 에러는 각 도메인 패키지의 enum(예: UserErrorCode)이 구현한다.
 */
public interface ErrorCode {

    HttpStatus getStatus();

    /** 프런트에서 분기할 때 쓰는 고정 값. 예: LISTING_ALREADY_RESERVED */
    String getCode();

    /** 사용자에게 보여줄 기본 문장 */
    String getMessage();
}
