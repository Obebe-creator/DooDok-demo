package com.doodok.domain.user;

import com.doodok.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class CurrentUserController {

    @ApiResponse(responseCode = "200", description = "현재 사용자 정보")
    @ApiResponse(responseCode = "401", description = "Access Token 누락·만료·무효",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/api/users/me")
    @SecurityRequirement(name = "bearerAuth")
    public MeResponse me(@Parameter(hidden = true) @AuthenticationPrincipal User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getProfileImageUrl(),
                user.getCreatedAt());
    }

    public record MeResponse(Long id, String email, String nickname, String profileImageUrl, Instant createdAt) {
    }
}
