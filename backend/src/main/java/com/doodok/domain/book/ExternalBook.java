package com.doodok.domain.book;

import java.time.LocalDate;

/** 외부 도서 API에서 받은 책 한 권. 값이 없는 항목은 null이다. */
public record ExternalBook(String isbn13, String title, String author, String publisher, LocalDate publishedDate,
        Integer listPrice, String coverImageUrl, String description) {
}
