package com.doodok.domain.book;

import com.doodok.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    /** 판매글·독후감을 쓸 때 책을 고르는 용도. 외부 API 한도를 지키려고 로그인한 회원만 쓴다. */
    @ApiResponse(responseCode = "200", description = "검색 결과")
    @ApiResponse(responseCode = "400", description = "요청 검증 실패",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "외부 도서 API 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/search")
    @SecurityRequirement(name = "bearerAuth")
    public BookSearchResponse search(
            @RequestParam @NotBlank(message = "필수 값입니다.") @Size(max = 100, message = "100자 이하로 입력해 주세요.")
            String query,
            // 카카오는 50페이지까지만 준다.
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "1 이상이어야 합니다.")
            @Max(value = 50, message = "50 이하여야 합니다.") int page) {
        return bookService.search(query, page);
    }

    @ApiResponse(responseCode = "200", description = "이미 등록된 도서")
    @ApiResponse(responseCode = "201", description = "새로 저장한 도서")
    @ApiResponse(responseCode = "400", description = "요청 검증 실패",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "외부 API에 없는 ISBN",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "외부 도서 API 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<BookResponse> register(@Valid @RequestBody BookRegisterRequest request) {
        BookService.Registration result = bookService.register(request.isbn13());
        if (!result.created()) {
            return ResponseEntity.ok(result.book());
        }
        return ResponseEntity.created(URI.create("/api/books/" + result.book().id())).body(result.book());
    }

    @ApiResponse(responseCode = "200", description = "도서 상세")
    @ApiResponse(responseCode = "404", description = "도서 없음",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{bookId}")
    public BookResponse get(@PathVariable Long bookId) {
        return bookService.get(bookId);
    }
}
