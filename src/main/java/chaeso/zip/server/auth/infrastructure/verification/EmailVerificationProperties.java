package chaeso.zip.server.auth.infrastructure.verification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.email-verification.* 설정. */
@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(
    String from,
    Duration codeTtl,
    Duration verifiedTtl,
    int maxVerifyAttempts,
    Duration sendCooldown) {
}
