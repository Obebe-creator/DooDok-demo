package com.doodok.domain.book;

import com.doodok.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BookErrorCode implements ErrorCode {

    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "도서를 찾을 수 없습니다."),
    BOOK_SEARCH_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "도서 검색이 잠시 원활하지 않습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
