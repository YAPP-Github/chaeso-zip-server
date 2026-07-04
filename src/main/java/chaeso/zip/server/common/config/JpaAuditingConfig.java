package chaeso.zip.server.common.config;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class JpaAuditingConfig {

  /** 감사 필드(created_at/updated_at)를 서버 기본 존이 아닌 UTC로 고정한다. */
  @Bean
  public DateTimeProvider utcDateTimeProvider() {
    return () -> Optional.of(LocalDateTime.now(ZoneOffset.UTC));
  }
}
