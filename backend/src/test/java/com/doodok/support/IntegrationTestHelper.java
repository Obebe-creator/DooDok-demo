package com.doodok.support;

import com.doodok.domain.auth.EmailVerificationService;
import com.doodok.domain.book.BookRepository;
import com.doodok.domain.user.UserRepository;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 통합 테스트에서 자주 쓰는 준비·정리 작업. {@link TestMailConfiguration}이 빈으로 등록한다. */
public class IntegrationTestHelper {

    private final UserRepository userRepository;
    private final StringRedisTemplate redis;
    private final FakeEmailSender emailSender;
    private final EmailVerificationService emailVerificationService;
    private final BookRepository bookRepository;
    private final FakeBookSearchClient bookSearchClient;

    public IntegrationTestHelper(UserRepository userRepository, StringRedisTemplate redis,
            FakeEmailSender emailSender, EmailVerificationService emailVerificationService,
            BookRepository bookRepository, FakeBookSearchClient bookSearchClient) {
        this.userRepository = userRepository;
        this.redis = redis;
        this.emailSender = emailSender;
        this.emailVerificationService = emailVerificationService;
        this.bookRepository = bookRepository;
        this.bookSearchClient = bookSearchClient;
    }

    /** 이메일 인증을 끝까지 진행하고 signupToken을 돌려준다. email은 정규화(소문자)된 값을 넣는다. */
    public String issueSignupToken(String email) {
        emailVerificationService.sendCode(email, "test-helper");
        String code = emailSender.latestCodeFor(email);
        return emailVerificationService.confirm(email, code).signupToken();
    }

    /** DB, Redis, 보낸 메일 기록, 가짜 도서 검색 데이터를 모두 비운다. */
    public void cleanUp() {
        bookRepository.deleteAllInBatch();
        bookSearchClient.clear();
        userRepository.deleteAllInBatch();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        emailSender.clear();
    }
}
