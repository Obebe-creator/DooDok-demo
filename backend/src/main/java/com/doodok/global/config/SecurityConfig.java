package com.doodok.global.config;

import com.doodok.domain.user.UserRepository;
import com.doodok.global.security.AccessTokens;
import com.doodok.global.security.JwtAuthenticationFilter;
import com.doodok.global.security.SecurityExceptionHandler;
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

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityExceptionHandler securityExceptionHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokens tokens, UserRepository users) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher publicRequests = new OrRequestMatcher(
                paths.matcher("/actuator/health"),
                paths.matcher(HttpMethod.POST, "/api/auth/email-verifications"),
                paths.matcher(HttpMethod.POST, "/api/auth/email-verifications/confirm"),
                paths.matcher(HttpMethod.POST, "/api/auth/signup"),
                paths.matcher(HttpMethod.POST, "/api/auth/login"),
                paths.matcher(HttpMethod.POST, "/api/auth/refresh"));
        http
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

    /**
     * 기본은 BCrypt. 저장값 앞에 {bcrypt} 같은 알고리즘 표시가 붙어서,
     * 나중에 알고리즘을 바꿔도 기존 비밀번호를 그대로 검증할 수 있다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
