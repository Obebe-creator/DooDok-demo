package com.doodok.domain.book;

import java.util.List;
import java.util.Optional;

/**
 * 외부 도서 검색. 구현을 바꿔 끼울 수 있도록 인터페이스로 둔다.
 * 실제 호출은 {@link KakaoBookSearchClient}, 테스트는 가짜 구현을 쓴다.
 * 외부 API 오류는 BookErrorCode.BOOK_SEARCH_UNAVAILABLE로 던진다.
 */
public interface BookSearchClient {

    /** ISBN-13이 있는 책만 돌려준다. (ISBN-13이 없으면 등록할 수 없다) */
    Page search(String query, int page);

    Optional<ExternalBook> findByIsbn13(String isbn13);

    record Page(List<ExternalBook> items, boolean hasNext) {
    }
}
