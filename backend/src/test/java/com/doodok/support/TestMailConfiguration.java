package com.doodok.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * 통합 테스트에서는 실제 SMTP·외부 도서 API 대신 {@link FakeEmailSender}, {@link FakeBookSearchClient}를 쓰고,
 * {@link IntegrationTestHelper}를 등록한다.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(IntegrationTestHelper.class)
public class TestMailConfiguration {

    @Bean
    @Primary
    FakeEmailSender fakeEmailSender() {
        return new FakeEmailSender();
    }

    @Bean
    @Primary
    FakeBookSearchClient fakeBookSearchClient() {
        return new FakeBookSearchClient();
    }
}
