package chaeso.zip.server.common.ratelimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.rate-limit.rules.* 설정.
 *
 * 규칙 이름은 맵 키, {@link RateLimited#value()} 와 1:1 대응
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    Map<String, RuleConfig> rules,
    Duration failClosedRetryAfter) {

  public record RuleConfig(int limit, Duration window, @DefaultValue("true") boolean failOpen) {
  }

  public RateLimitRule rule(RateLimitPolicy policy) {
    return rule(policy.getKey());
  }

  public RateLimitRule rule(String name) {
    RuleConfig config = rules == null ? null : rules.get(name);
    if (config == null) {
      throw new IllegalStateException(
          "app.rate-limit.rules 에 정의되지 않은 규칙: " + name);
    }
    return new RateLimitRule(name, config.limit(), config.window(), config.failOpen());
  }
}