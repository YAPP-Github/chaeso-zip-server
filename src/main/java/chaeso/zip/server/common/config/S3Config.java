package chaeso.zip.server.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 애플리케이션 공통 AWS S3 클라이언트 및 Presigner 빈 설정.
 */
@Configuration
public class S3Config {

  @Bean
  public Region awsRegion(@Value("${aws.region:${AWS_REGION:ap-northeast-2}}") String region) {
    return Region.of(region);
  }

  @Bean
  public AwsCredentialsProvider awsCredentialsProvider() {
    return DefaultCredentialsProvider.builder().build();
  }

  @Bean
  public S3Client s3Client(Region region, AwsCredentialsProvider credentialsProvider) {
    return S3Client.builder()
        .region(region)
        .credentialsProvider(credentialsProvider)
        .build();
  }

  @Bean
  public S3Presigner s3Presigner(Region region, AwsCredentialsProvider credentialsProvider) {
    return S3Presigner.builder()
        .region(region)
        .credentialsProvider(credentialsProvider)
        .build();
  }
}
