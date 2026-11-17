package com.doodok.domain.auth;

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
    @PostMapping("/email-verifications")
    public ResponseEntity<EmailVerificationResponse> sendEmailVerificationCode(
            @Valid @RequestBody EmailVerificationRequest request) {
        return ResponseEntity.accepted().body(emailVerificationService.sendCode(request.email()));
    }

    /** 인증 코드 확인 → signupToken 발급 */
    @PostMapping("/email-verifications/confirm")
    public SignupTokenResponse confirmEmailVerificationCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request) {
        return emailVerificationService.confirm(request.email(), request.code());
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity
                .created(URI.create("/api/users/" + response.id()))
                .body(response);
    }
}
