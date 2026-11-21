package gachi.chagok_server.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * JPA 감사(createdAt/updatedAt), 비동기 서류 분석, 기한 알림 스케줄러를 켠다.
 */
@Configuration
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class AppConfig {
}
