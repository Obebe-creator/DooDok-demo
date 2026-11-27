package com.doodok.domain.book;

import java.util.List;

/** 외부 검색 결과를 그대로 보여준다. DB에는 저장하지 않는다. */
public record BookSearchResponse(List<Item> items, int page, boolean hasNext) {

    public record Item(String isbn13, String title, String author, String publisher, String coverImageUrl,
            Integer listPrice) {
    }

    static BookSearchResponse of(BookSearchClient.Page result, int page) {
        List<Item> items = result.items().stream()
                .map(b -> new Item(b.isbn13(), b.title(), b.author(), b.publisher(), b.coverImageUrl(), b.listPrice()))
                .toList();
        return new BookSearchResponse(items, page, result.hasNext());
    }
}
