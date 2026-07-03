package chaeso.zip.server.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(String from, Duration codeTtl, Duration verifiedTtl) {
}
