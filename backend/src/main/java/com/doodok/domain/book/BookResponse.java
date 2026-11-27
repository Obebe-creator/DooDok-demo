package com.doodok.domain.book;

import java.time.LocalDate;

public record BookResponse(Long id, String isbn13, String title, String author, String publisher,
        LocalDate publishedDate, Integer listPrice, String coverImageUrl, String description, String category) {

    public static BookResponse from(Book book) {
        return new BookResponse(book.getId(), book.getIsbn13(), book.getTitle(), book.getAuthor(),
                book.getPublisher(), book.getPublishedDate(), book.getListPrice(), book.getCoverImageUrl(),
                book.getDescription(), book.getCategory());
    }
}
