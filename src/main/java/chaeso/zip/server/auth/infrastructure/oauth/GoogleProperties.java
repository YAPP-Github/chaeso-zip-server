package chaeso.zip.server.auth.infrastructure.oauth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OAuth 설정 바인딩. signupTtl 은 idToken 만료(약 1시간)보다 짧고 가입 폼 작성 시간보다는 길게 잡는다.
 */
@ConfigurationProperties(prefix = "app.google")
public record GoogleProperties(String clientId, Duration signupTtl) {
}
