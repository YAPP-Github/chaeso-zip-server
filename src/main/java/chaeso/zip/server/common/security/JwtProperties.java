package chaeso.zip.server.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 바인딩. secret은 HS256용 최소 256bit(32바이트) 이상이어야 한다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}
