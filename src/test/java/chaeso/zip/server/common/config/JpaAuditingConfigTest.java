package chaeso.zip.server.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JpaAuditingConfigTest {

  @Test
  @DisplayName("감사 시각 제공자는 JVM 기본 시간대가 아니라 UTC 기준 시각을 준다")
  void providesUtcTime() {
    LocalDateTime expected = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);

    TemporalAccessor actual = new JpaAuditingConfig().utcDateTimeProvider().getNow().orElseThrow();

    assertThat(Duration.between(expected, LocalDateTime.from(actual)).abs())
        .isLessThan(Duration.ofSeconds(10));
  }
}
