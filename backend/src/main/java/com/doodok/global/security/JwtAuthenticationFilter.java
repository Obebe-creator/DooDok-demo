package com.doodok.global.security;

import com.doodok.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AccessTokens tokens;
    private final UserRepository users;
    private final SecurityExceptionHandler exceptionHandler;
    private final RequestMatcher publicRequests;

    public JwtAuthenticationFilter(AccessTokens tokens, UserRepository users,
                                   SecurityExceptionHandler exceptionHandler, RequestMatcher publicRequests) {
        this.tokens = tokens;
        this.users = users;
        this.exceptionHandler = exceptionHandler;
        this.publicRequests = publicRequests;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            Optional<Long> userId;
            try {
                userId = tokens.verify(authorization.substring(7));
            } catch (CredentialsExpiredException exception) {
                if (publicRequests.matches(request)) {
                    chain.doFilter(request, response);
                } else {
                    exceptionHandler.commence(request, response, exception);
                }
                return;
            }
            userId.flatMap(users::findByIdAndDeletedAtIsNull).ifPresent(user -> {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null,
                        AuthorityUtils.createAuthorityList("ROLE_" + user.getRole().name())));
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
