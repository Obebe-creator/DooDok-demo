package com.doodok.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * {@code @CreatedDate}, {@code @LastModifiedDate}를 켠다.
 * 메인 클래스에 붙이지 않고 분리해 두면, JPA 없이 웹 계층만 띄우는 테스트에서 오류가 나지 않는다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
