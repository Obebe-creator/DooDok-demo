package com.doodok.support;

import com.doodok.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합 테스트 공통 설정: 앱 전체 + PostgreSQL·Redis 컨테이너 + MockMvc + 가짜 메일 발송기.
 * 모든 통합 테스트가 같은 설정을 쓰면 스프링이 컨텍스트(와 컨테이너)를 한 번만 만들어 재사용하므로 테스트가 빨라진다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
public @interface IntegrationTest {
}
