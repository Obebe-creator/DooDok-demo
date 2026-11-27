package com.doodok.domain.book;

import com.doodok.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 외부 API 호출이 DB 트랜잭션(커넥션)을 붙잡고 있지 않도록 클래스에 @Transactional을 걸지 않는다.
 * 조회·저장은 각 Repository 메서드의 트랜잭션으로 충분하다.
 */
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final BookSearchClient bookSearchClient;

    public BookSearchResponse search(String query, int page) {
        BookSearchClient.Page result = bookSearchClient.search(query.strip(), page);
        return BookSearchResponse.of(result, page);
    }

    /** 이미 있으면 기존 도서를, 없으면 외부 API에서 가져와 저장한다. */
    public Registration register(String isbn13) {
        var existing = bookRepository.findByIsbn13(isbn13);
        if (existing.isPresent()) {
            return new Registration(BookResponse.from(existing.get()), false);
        }
        ExternalBook source = bookSearchClient.findByIsbn13(isbn13)
                .orElseThrow(() -> new BusinessException(BookErrorCode.BOOK_NOT_FOUND));
        try {
            return new Registration(BookResponse.from(bookRepository.saveAndFlush(Book.from(source))), true);
        } catch (DataIntegrityViolationException e) {
            // 같은 ISBN을 동시에 등록한 다른 요청이 먼저 저장했다. 그 행을 돌려준다.
            Book saved = bookRepository.findByIsbn13(isbn13).orElseThrow(() -> e);
            return new Registration(BookResponse.from(saved), false);
        }
    }

    public BookResponse get(Long bookId) {
        return bookRepository.findById(bookId)
                .map(BookResponse::from)
                .orElseThrow(() -> new BusinessException(BookErrorCode.BOOK_NOT_FOUND));
    }

    public record Registration(BookResponse book, boolean created) {
    }
}
