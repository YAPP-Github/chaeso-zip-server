package chaeso.zip.server.onboarding.infrastructure.s3;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 온보딩 성과파일 업로드용 S3 버킷 설정 바인딩.
 */
@ConfigurationProperties(prefix = "app.onboarding.s3")
public record OnboardingS3Properties(String bucket, Duration presignTtl) {
}
