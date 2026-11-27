package com.doodok.domain.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.doodok.domain.user.User;
import com.doodok.domain.user.UserRepository;
import com.doodok.global.security.AccessTokens;
import com.doodok.support.FakeBookSearchClient;
import com.doodok.support.IntegrationTest;
import com.doodok.support.IntegrationTestHelper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class BookApiTest {

    private static final ExternalBook DEMIAN = new ExternalBook("9788937460449", "데미안", "헤르만 헤세", "민음사",
            LocalDate.of(2000, 12, 20), 9000, "https://image/demian.jpg", "싱클레어의 성장 이야기");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeBookSearchClient bookSearchClient;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookService bookService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessTokens accessTokens;

    @Autowired
    private IntegrationTestHelper helper;

    private String bearer;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.create("reader@doodok.com", "{noop}x", "책읽는곰"));
        bearer = "Bearer " + accessTokens.issue(user.getId());
        bookSearchClient.add(DEMIAN);
    }

    @AfterEach
    void tearDown() {
        helper.cleanUp();
    }

    @Test
    @DisplayName("검색은 외부 결과를 그대로 돌려주고 DB에 저장하지 않는다")
    void search() throws Exception {
        mockMvc.perform(get("/api/books/search").param("query", "데미안").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].isbn13").value("9788937460449"))
                .andExpect(jsonPath("$.items[0].listPrice").value(9000))
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        assertThat(bookRepository.count()).isZero();
    }

    @Test
    @DisplayName("검색은 로그인이 필요하고, 검색어와 페이지를 검증한다")
    void searchValidation() throws Exception {
        mockMvc.perform(get("/api/books/search").param("query", "데미안"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/books/search").param("query", " ").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/books/search").param("query", "데미안").param("page", "51")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    @DisplayName("외부 API 오류는 502 BOOK_SEARCH_UNAVAILABLE")
    void searchUnavailable() throws Exception {
        bookSearchClient.setUnavailable(true);

        mockMvc.perform(get("/api/books/search").param("query", "데미안").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BOOK_SEARCH_UNAVAILABLE"));
    }

    @Test
    @DisplayName("처음 등록하면 201로 저장하고, 다시 등록하면 외부 API 없이 200으로 기존 도서를 준다")
    void register() throws Exception {
        register("9788937460449")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/books/\\d+")))
                .andExpect(jsonPath("$.title").value("데미안"))
                .andExpect(jsonPath("$.publishedDate").value("2000-12-20"));

        bookSearchClient.setUnavailable(true);
        register("9788937460449").andExpect(status().isOk());

        assertThat(bookRepository.count()).isOne();
        assertThat(bookSearchClient.isbnLookups()).isOne();
    }

    @Test
    @DisplayName("외부 API에 없는 ISBN은 404, 형식이 틀리면 400")
    void registerInvalid() throws Exception {
        register("9780000000000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
        register("97889374604")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("isbn13"));
    }

    @Test
    @DisplayName("같은 ISBN을 동시에 등록해도 한 행만 저장하고 모두 같은 도서를 받는다")
    void registerConcurrently() throws Exception {
        int threads = 8;
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        List<Future<BookService.Registration>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit((Callable<BookService.Registration>) () -> {
                    ready.countDown();
                    start.await();
                    return bookService.register("9788937460449");
                }));
            }
            ready.await();
            start.countDown();
        }

        List<BookService.Registration> registrations = new ArrayList<>();
        for (var result : results) {
            registrations.add(result.get());
        }
        assertThat(bookRepository.count()).isOne();
        assertThat(registrations).extracting(r -> r.book().id()).containsOnly(registrations.getFirst().book().id());
        assertThat(registrations).filteredOn(BookService.Registration::created).hasSize(1);
    }

    @Test
    @DisplayName("도서 상세는 로그인 없이 볼 수 있고, 없으면 404")
    void detail() throws Exception {
        Long id = bookService.register("9788937460449").book().id();

        mockMvc.perform(get("/api/books/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("싱클레어의 성장 이야기"));
        mockMvc.perform(get("/api/books/{id}", id + 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    private ResultActions register(String isbn13) throws Exception {
        return mockMvc.perform(post("/api/books")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isbn13\":\"" + isbn13 + "\"}"));
    }
}
