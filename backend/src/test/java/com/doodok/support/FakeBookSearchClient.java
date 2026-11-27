package com.doodok.support;

import com.doodok.domain.book.BookErrorCode;
import com.doodok.domain.book.BookSearchClient;
import com.doodok.domain.book.ExternalBook;
import com.doodok.global.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 테스트용 도서 검색. 외부 API를 부르지 않고 add()로 넣은 책만 돌려준다. */
public class FakeBookSearchClient implements BookSearchClient {

    private final Map<String, ExternalBook> books = new ConcurrentHashMap<>();
    private final AtomicBoolean unavailable = new AtomicBoolean();
    private final AtomicInteger isbnLookups = new AtomicInteger();

    public void add(ExternalBook book) {
        books.put(book.isbn13(), book);
    }

    /** true면 모든 호출이 외부 API 오류(502)로 실패한다. */
    public void setUnavailable(boolean value) {
        unavailable.set(value);
    }

    public int isbnLookups() {
        return isbnLookups.get();
    }

    @Override
    public Page search(String query, int page) {
        checkAvailable();
        List<ExternalBook> found = books.values().stream().filter(b -> b.title().contains(query)).toList();
        return new Page(found, false);
    }

    @Override
    public Optional<ExternalBook> findByIsbn13(String isbn13) {
        checkAvailable();
        isbnLookups.incrementAndGet();
        return Optional.ofNullable(books.get(isbn13));
    }

    private void checkAvailable() {
        if (unavailable.get()) {
            throw new BusinessException(BookErrorCode.BOOK_SEARCH_UNAVAILABLE);
        }
    }

    public void clear() {
        books.clear();
        unavailable.set(false);
        isbnLookups.set(0);
    }
}
