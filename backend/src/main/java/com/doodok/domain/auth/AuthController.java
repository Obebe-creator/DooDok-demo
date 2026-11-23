package com.doodok.domain.auth;

import com.doodok.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    /** 인증 코드 발송. 메일은 비동기로 보내므로 202 Accepted */
    @ApiResponse(responseCode = "202", description = "인증 코드 발송 접수")
    @ApiResponse(responseCode = "400", description = "요청 검증 실패",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "인증 코드 요청 한도 초과",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/email-verifications")
    public ResponseEntity<EmailVerificationResponse> sendEmailVerificationCode(
            @Valid @RequestBody EmailVerificationRequest request, HttpServletRequest httpRequest) {
        // 지금은 프록시 없이 바로 받으므로 getRemoteAddr()가 실제 IP다.
        // 배포에서 Nginx·로드밸런서 뒤에 두면 server.forward-headers-strategy 설정으로 실제 IP를 받게 해야 한다.
        EmailVerificationResponse response = emailVerificationService.sendCode(request.email(), httpRequest.getRemoteAddr());
        return ResponseEntity.accepted().body(response);
    }

    /** 인증 코드 확인 → signupToken 발급 */
    @ApiResponse(responseCode = "200", description = "가입 토큰 발급")
    @ApiResponse(responseCode = "400", description = "요청 검증 실패 또는 인증 코드 불일치·만료",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "인증 코드 입력 한도 초과",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/email-verifications/confirm")
    public SignupTokenResponse confirmEmailVerificationCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request) {
        return emailVerificationService.confirm(request.email(), request.code());
    }

    @ApiResponse(responseCode = "201", description = "가입 성공")
    @ApiResponse(responseCode = "400", description = "요청 검증 실패 또는 가입 토큰 무효",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "이메일 또는 닉네임 중복",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity
                .created(URI.create("/api/users/" + response.id()))
                .body(response);
    }
}
