package com.doodok.global.exception;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스프링 컨텍스트 없이 테스트용 컨트롤러와 예외 핸들러만 올려서 에러 응답 형식을 검증한다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("BusinessException은 에러 코드의 상태와 코드, 메시지로 응답한다")
    void businessException() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEST_CONFLICT"))
                .andExpect(jsonPath("$.message").value("테스트 충돌입니다."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @DisplayName("BusinessException에 직접 넣은 메시지가 기본 메시지보다 우선한다")
    void businessExceptionWithCustomMessage() throws Exception {
        mockMvc.perform(get("/test/business-custom"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEST_CONFLICT"))
                .andExpect(jsonPath("$.message").value("상황에 맞춘 메시지"));
    }

    @Test
    @DisplayName("요청 본문 검증에 실패하면 필드별 사유를 errors에 담는다")
    void requestBodyValidation() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"price\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "price")))
                .andExpect(jsonPath("$.errors[*].reason", containsInAnyOrder("이름은 필수입니다.", "가격은 0 이상이어야 합니다.")));
    }

    @Test
    @DisplayName("JSON 형식이 깨지면 INVALID_INPUT으로 응답한다")
    void notReadableBody() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다. 형식을 확인해 주세요."));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터가 없으면 해당 필드를 알려준다")
    void missingParameter() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].reason").value("필수 값입니다."));
    }

    @Test
    @DisplayName("쿼리 파라미터 타입이 맞지 않으면 해당 필드를 알려준다")
    void typeMismatch() throws Exception {
        mockMvc.perform(get("/test/param").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].reason").value("형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405로 응답한다")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(delete("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 내부 메시지를 숨기고 고정 문구로 응답한다")
    void unexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.INTERNAL_ERROR.getMessage()));
    }

    // ===== 테스트용 컨트롤러와 에러 코드 =====

    enum TestErrorCode implements ErrorCode {
        TEST_CONFLICT;

        @Override
        public HttpStatus getStatus() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String getCode() {
            return name();
        }

        @Override
        public String getMessage() {
            return "테스트 충돌입니다.";
        }
    }

    public record TestRequest(
            @NotBlank(message = "이름은 필수입니다.") String name,
            @Min(value = 0, message = "가격은 0 이상이어야 합니다.") int price) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        String business() {
            throw new BusinessException(TestErrorCode.TEST_CONFLICT);
        }

        @GetMapping("/test/business-custom")
        String businessCustom() {
            throw new BusinessException(TestErrorCode.TEST_CONFLICT, "상황에 맞춘 메시지");
        }

        @PostMapping("/test/validation")
        String validation(@Valid @RequestBody TestRequest request) {
            return "ok";
        }

        @GetMapping("/test/param")
        String param(@RequestParam int size) {
            return "ok";
        }

        @GetMapping("/test/unexpected")
        String unexpected() {
            throw new IllegalStateException("DB 비밀번호 같은 내부 정보");
        }
    }
}
