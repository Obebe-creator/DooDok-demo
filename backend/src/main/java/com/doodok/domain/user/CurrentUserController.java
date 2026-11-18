package com.doodok.domain.user;

import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentUserController {

    @GetMapping("/api/users/me")
    public MeResponse me(@AuthenticationPrincipal User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getProfileImageUrl(),
                user.getCreatedAt());
    }

    public record MeResponse(Long id, String email, String nickname, String profileImageUrl, Instant createdAt) {
    }
}
