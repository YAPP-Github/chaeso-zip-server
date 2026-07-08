package chaeso.zip.server.auth.infrastructure.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 바인딩. secret은 Base64URL 인코딩 문자열이어야 한다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl) {
}
