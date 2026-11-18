package com.doodok.domain.auth;

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
    @PostMapping("/email-verifications")
    public ResponseEntity<EmailVerificationResponse> sendEmailVerificationCode(
            @Valid @RequestBody EmailVerificationRequest request, HttpServletRequest httpRequest) {
        // 지금은 프록시 없이 바로 받으므로 getRemoteAddr()가 실제 IP다.
        // 배포에서 Nginx·로드밸런서 뒤에 두면 server.forward-headers-strategy 설정으로 실제 IP를 받게 해야 한다.
        EmailVerificationResponse response = emailVerificationService.sendCode(request.email(), httpRequest.getRemoteAddr());
        return ResponseEntity.accepted().body(response);
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
