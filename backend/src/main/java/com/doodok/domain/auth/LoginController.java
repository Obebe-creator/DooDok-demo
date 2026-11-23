package com.doodok.domain.auth;

import com.doodok.global.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final LoginService service;
    private final boolean secureCookie;

    public LoginController(LoginService service, @Value("${doodok.auth.secure-cookie:true}") boolean secureCookie) {
        this.service = service;
        this.secureCookie = secureCookie;
    }

    @ApiResponse(responseCode = "200", description = "Access Token 발급 및 Refresh 쿠키 설정")
    @ApiResponse(responseCode = "400", description = "요청 본문 형식 오류",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "자격증명 불일치",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "429", description = "로그인 요청 한도 초과",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public ResponseEntity<LoginService.TokenResponse> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        return tokens(service.login(request.email(), request.password(), http.getRemoteAddr()));
    }

    @ApiResponse(responseCode = "200", description = "Access Token 재발급 및 Refresh 쿠키 교체")
    @ApiResponse(responseCode = "401", description = "Refresh 토큰 누락·만료·무효",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/refresh")
    public ResponseEntity<LoginService.TokenResponse> refresh(@CookieValue(name = "refreshToken", required = false) String token) {
        return tokens(service.refresh(token));
    }

    @ApiResponse(responseCode = "204", description = "로그아웃 및 Refresh 쿠키 만료", content = @Content)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "refreshToken", required = false) String token) {
        service.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO)).build();
    }

    private ResponseEntity<LoginService.TokenResponse> tokens(LoginService.Session session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), LoginService.REFRESH_TTL))
                .body(session.response());
    }

    private String cookie(String token, Duration age) {
        return ResponseCookie.from("refreshToken", token).httpOnly(true).secure(secureCookie)
                .sameSite("Strict").path("/api/auth").maxAge(age).build().toString();
    }

    public record LoginRequest(String email, String password) {
    }
}
