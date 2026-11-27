package com.doodok.domain.book;

import com.doodok.global.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * 카카오 책 검색 API. https://developers.kakao.com/docs/latest/ko/daum-search/dev-guide#search-book
 * 외부 API가 느려도 요청 스레드가 오래 묶이지 않도록 타임아웃을 짧게 둔다.
 */
@Slf4j
@Component
public class KakaoBookSearchClient implements BookSearchClient {

    static final int PAGE_SIZE = 10;

    private final RestClient restClient;

    @Autowired
    public KakaoBookSearchClient(@Value("${doodok.book.kakao-api-key}") String apiKey) {
        this(RestClient.builder().requestFactory(timeouts()), apiKey);
    }

    /** 테스트에서 MockRestServiceServer를 붙인 builder를 넣는다. */
    KakaoBookSearchClient(RestClient.Builder builder, String apiKey) {
        this.restClient = builder
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
                .build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return factory;
    }

    @Override
    public Page search(String query, int page) {
        KakaoResponse response = call(uri -> uri
                .queryParam("query", query)
                .queryParam("page", page)
                .queryParam("size", PAGE_SIZE));
        List<ExternalBook> items = response.documents().stream()
                .map(KakaoBookSearchClient::toBook)
                .filter(Objects::nonNull)
                .toList();
        return new Page(items, !response.meta().isEnd());
    }

    @Override
    public Optional<ExternalBook> findByIsbn13(String isbn13) {
        KakaoResponse response = call(uri -> uri
                .queryParam("query", isbn13)
                .queryParam("target", "isbn"));
        return response.documents().stream()
                .map(KakaoBookSearchClient::toBook)
                .filter(book -> book != null && book.isbn13().equals(isbn13))
                .findFirst();
    }

    private KakaoResponse call(Function<UriBuilder, UriBuilder> params) {
        try {
            KakaoResponse response = restClient.get()
                    .uri(uri -> params.apply(uri.path("/v3/search/book")).build())
                    .retrieve()
                    .body(KakaoResponse.class);
            if (response == null || response.documents() == null || response.meta() == null) {
                throw new RestClientException("Empty response body");
            }
            return response;
        } catch (RestClientException e) {
            log.warn("Kakao book search failed: {}", e.getMessage());
            throw new BusinessException(BookErrorCode.BOOK_SEARCH_UNAVAILABLE);
        }
    }

    /** ISBN-13이나 제목이 없는 문서는 null */
    static ExternalBook toBook(Document doc) {
        String isbn13 = Arrays.stream(nullToEmpty(doc.isbn()).split(" "))
                .filter(token -> token.matches("\\d{13}"))
                .findFirst()
                .orElse(null);
        if (isbn13 == null || blankToNull(doc.title()) == null) {
            return null;
        }
        String author = doc.authors() == null ? null : blankToNull(String.join(", ", doc.authors()));
        Integer price = doc.price() != null && doc.price() > 0 ? doc.price() : null;
        return new ExternalBook(isbn13, doc.title().strip(), author, blankToNull(doc.publisher()),
                parseDate(doc.datetime()), price, blankToNull(doc.thumbnail()), blankToNull(doc.contents()));
    }

    private static LocalDate parseDate(String datetime) {
        if (blankToNull(datetime) == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(datetime).toLocalDate();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    record KakaoResponse(Meta meta, List<Document> documents) {
    }

    record Meta(@JsonProperty("is_end") boolean isEnd) {
    }

    /** isbn은 "ISBN10 ISBN13" 형식으로 공백으로 구분되며, 둘 중 하나가 비어 있을 수 있다. */
    record Document(String title, String contents, String isbn, String datetime, List<String> authors,
            String publisher, Integer price, String thumbnail) {
    }
}
