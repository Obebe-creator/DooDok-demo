package com.doodok.domain.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.doodok.global.exception.BusinessException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 카카오 응답 → ExternalBook 변환과 오류 처리. 실제 카카오는 부르지 않는다. */
class KakaoBookSearchClientTest {

    private static final String RESPONSE = """
            {
              "meta": { "is_end": false, "pageable_count": 30, "total_count": 30 },
              "documents": [
                {
                  "title": "데미안", "contents": "싱클레어의 성장 이야기", "isbn": "8937460440 9788937460449",
                  "datetime": "2000-12-20T00:00:00.000+09:00", "authors": ["헤르만 헤세"], "publisher": "민음사",
                  "translators": ["전영애"], "price": 9000, "sale_price": 8100, "thumbnail": "https://image/demian.jpg"
                },
                {
                  "title": "ISBN-10만 있는 옛날 책", "contents": "", "isbn": "8937460440 ",
                  "datetime": "", "authors": [], "publisher": "", "price": 0, "thumbnail": ""
                },
                {
                  "title": "저자 둘", "contents": "", "isbn": " 9791100000001",
                  "datetime": "", "authors": ["가", "나"], "publisher": "", "price": -1, "thumbnail": ""
                }
              ]
            }
            """;

    private MockRestServiceServer server;
    private KakaoBookSearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoBookSearchClient(builder, "test-key");
    }

    @Test
    void searchMapsDocumentsAndSkipsBooksWithoutIsbn13() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://dapi.kakao.com/v3/search/book")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-key"))
                .andExpect(queryParam("page", "2"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        BookSearchClient.Page page = client.search("데미안", 2);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).containsExactly(
                new ExternalBook("9788937460449", "데미안", "헤르만 헤세", "민음사", LocalDate.of(2000, 12, 20), 9000,
                        "https://image/demian.jpg", "싱클레어의 성장 이야기"),
                new ExternalBook("9791100000001", "저자 둘", "가, 나", null, null, null, null, null));
        server.verify();
    }

    @Test
    void findByIsbn13ReturnsOnlyExactMatch() {
        server.expect(queryParam("target", "isbn"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        assertThat(client.findByIsbn13("9788937460449")).map(ExternalBook::title).contains("데미안");

        server.reset();
        server.expect(queryParam("target", "isbn"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        assertThat(client.findByIsbn13("9780000000000")).isEmpty();
    }

    @Test
    void serverErrorBecomesBookSearchUnavailable() {
        server.expect(queryParam("page", "1")).andRespond(withServerError());

        assertThatThrownBy(() -> client.search("데미안", 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BookErrorCode.BOOK_SEARCH_UNAVAILABLE);
    }
}
