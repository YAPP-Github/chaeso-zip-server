package chaeso.zip.server.auth.infrastructure.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.login-method-lookup.* 설정. */
@ConfigurationProperties(prefix = "app.login-method-lookup")
public record LoginMethodLookupProperties(
    int maxAttempts,
    Duration window) {
}
