package chaeso.zip.server.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RateLimitPropertiesTest {

  @Nested
  @DisplayName("규칙 이름으로 조회")
  class RuleTest {

    @Test
    @DisplayName("등록된 규칙 이름으로 조회 시 이름, 한도, 윈도우 시간이 포함된 RateLimitRule 객체를 반환한다")
    void rule_found() {
      RateLimitProperties properties = new RateLimitProperties(
          Map.of("onboarding-submit",
              new RateLimitProperties.RuleConfig(5, Duration.ofMinutes(1), true)),
          Duration.ofSeconds(5));

      RateLimitRule rule = properties.rule("onboarding-submit");

      assertThat(rule).isEqualTo(
          new RateLimitRule("onboarding-submit", 5, Duration.ofMinutes(1), true));
    }

    @Test
    @DisplayName("failOpen을 false로 등록하면 조회한 RateLimitRule에도 그대로 반영된다")
    void rule_failOpenFalse_isPropagated() {
      RateLimitProperties properties = new RateLimitProperties(
          Map.of("auth-login", new RateLimitProperties.RuleConfig(10, Duration.ofMinutes(1), false)),
          Duration.ofSeconds(5));

      RateLimitRule rule = properties.rule("auth-login");

      assertThat(rule.failOpen()).isFalse();
    }

    @Test
    @DisplayName("등록되지 않은 규칙 이름을 조회하면 IllegalStateException 예외가 발생한다")
    void rule_notFound_throws() {
      RateLimitProperties properties = new RateLimitProperties(Map.of(), Duration.ofSeconds(5));

      assertThatThrownBy(() -> properties.rule("unknown-rule"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("unknown-rule");
    }
  }
}