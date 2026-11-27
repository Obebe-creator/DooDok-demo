package com.doodok.domain.book;

import com.doodok.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ISBN-13 기준 도서 마스터. 같은 ISBN은 uk_books_isbn13 제약으로 한 행만 저장된다.
 * 스키마: db/migration/V5__create_books.sql
 */
@Entity
@Table(name = "books")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Book extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 13)
    private String isbn13;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 500)
    private String author;

    private String publisher;

    private LocalDate publishedDate;

    private Integer listPrice;

    @Column(length = 1000)
    private String coverImageUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 100)
    private String category;

    private Book(ExternalBook source) {
        this.isbn13 = source.isbn13();
        this.title = source.title();
        this.author = source.author();
        this.publisher = source.publisher();
        this.publishedDate = source.publishedDate();
        this.listPrice = source.listPrice();
        this.coverImageUrl = source.coverImageUrl();
        this.description = source.description();
    }

    /** 외부 도서 API 결과로 만든다. 카카오는 분류를 주지 않아 category는 비워 둔다. */
    public static Book from(ExternalBook source) {
        return new Book(source);
    }
}
