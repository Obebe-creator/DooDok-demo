package com.doodok.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** {@code @Async} 메서드를 별도 스레드에서 실행한다. (메일 발송 등) */
@Configuration
@EnableAsync
public class AsyncConfig {
}
