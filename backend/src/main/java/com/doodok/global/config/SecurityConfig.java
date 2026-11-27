package com.doodok.global.config;

import com.doodok.domain.user.UserRepository;
import com.doodok.global.security.AccessTokens;
import com.doodok.global.security.JwtAuthenticationFilter;
import com.doodok.global.security.SecurityExceptionHandler;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityExceptionHandler securityExceptionHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokens tokens, UserRepository users,
            CorsConfigurationSource apiCorsConfigurationSource) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher publicRequests = new OrRequestMatcher(
                paths.matcher("/actuator/health"),
                paths.matcher(HttpMethod.GET, "/v3/api-docs"),
                paths.matcher(HttpMethod.GET, "/v3/api-docs.yaml"),
                paths.matcher(HttpMethod.GET, "/v3/api-docs/**"),
                paths.matcher(HttpMethod.GET, "/swagger-ui.html"),
                paths.matcher(HttpMethod.GET, "/swagger-ui/**"),
                paths.matcher(HttpMethod.POST, "/api/auth/email-verifications"),
                paths.matcher(HttpMethod.POST, "/api/auth/email-verifications/confirm"),
                paths.matcher(HttpMethod.POST, "/api/auth/signup"),
                paths.matcher(HttpMethod.POST, "/api/auth/login"),
                paths.matcher(HttpMethod.POST, "/api/auth/refresh"),
                paths.matcher(HttpMethod.POST, "/api/auth/logout"),
                // 숫자만 허용해서 GET /api/books/search(로그인 필요)와 겹치지 않게 한다.
                paths.matcher(HttpMethod.GET, "/api/books/{bookId:\\d+}"));
        http
                .cors(cors -> cors.configurationSource(apiCorsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(new JwtAuthenticationFilter(tokens, users, securityExceptionHandler, publicRequests),
                        UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicRequests).permitAll()
                        .anyRequest().authenticated());

        return http.build();
    }

    @Bean
    CorsConfigurationSource apiCorsConfigurationSource(@Value("${doodok.cors.allowed-origins:}") String allowedOrigins) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowCredentials(true);
        config.validateAllowCredentials();
        config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Location"));
        // 프리플라이트(OPTIONS) 결과를 브라우저가 1시간 캐시한다. 매 요청 앞의 OPTIONS 왕복을 줄인다. (Chrome은 최대 2시간)
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * 기본은 BCrypt. 저장값 앞에 {bcrypt} 같은 알고리즘 표시가 붙어서,
     * 나중에 알고리즘을 바꿔도 기존 비밀번호를 그대로 검증할 수 있다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
