package com.doodok.domain.auth;

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

    @PostMapping("/login")
    public ResponseEntity<LoginService.TokenResponse> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        return tokens(service.login(request.email(), request.password(), http.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginService.TokenResponse> refresh(@CookieValue(name = "refreshToken", required = false) String token) {
        return tokens(service.refresh(token));
    }

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
