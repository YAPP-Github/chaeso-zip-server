package chaeso.zip.server.auth.infrastructure.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 바인딩. secret은 Base64URL 인코딩 문자열이어야 한다.
 *
 * @param refreshTtl         Refresh Token의 비활성 타임아웃. 토큰이 회전될 때마다 갱신된다.
 * @param refreshAbsoluteTtl Refresh Token family의 절대 수명. 로그인 시점부터 계산되며 회전이 일어나도 연장되지 않는다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secret,
    Duration accessTtl,
    Duration refreshTtl,
    Duration refreshAbsoluteTtl) {
}
