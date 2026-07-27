package chaeso.zip.server.onboarding.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import chaeso.zip.server.onboarding.application.InvalidPerformanceFileException;
import chaeso.zip.server.onboarding.application.dto.PresignPerformanceFileCommand;
import chaeso.zip.server.onboarding.application.dto.PresignedFileUploadResult;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3PerformanceFileStorageTest {

  @Mock
  private S3Client s3Client;

  @Mock
  private S3Presigner s3Presigner;

  @Mock
  private PresignedPutObjectRequest presignedRequest;

  private S3PerformanceFileStorage storage;

  @BeforeEach
  void setUp() {
    OnboardingS3Properties properties =
        new OnboardingS3Properties("test-bucket", Duration.ofMinutes(5));
    storage = new S3PerformanceFileStorage(s3Client, s3Presigner, properties);
  }

  @Test
  @DisplayName("presign()은 ad-history/ prefix와 원본 확장자를 유지한 key를 발급한다")
  void presignReturnsKeyWithExtension() throws Exception {
    given(s3Presigner.presignPutObject(
        ArgumentMatchers.<Consumer<PutObjectPresignRequest.Builder>>any()))
        .willReturn(presignedRequest);
    given(presignedRequest.url())
        .willReturn(URI.create("https://test-bucket.s3.amazonaws.com/x").toURL());
    given(presignedRequest.expiration()).willReturn(Instant.parse("2026-07-27T00:05:00Z"));

    List<PresignedFileUploadResult> results = storage.presign(List.of(
        new PresignPerformanceFileCommand("실적.xlsx", 1024L)));

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().key()).startsWith("ad-history/").endsWith(".xlsx");
    assertThat(results.getFirst().uploadUrl())
        .isEqualTo("https://test-bucket.s3.amazonaws.com/x");
    assertThat(results.getFirst().contentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  }

  @Test
  @DisplayName("verify()는 10MB 이하 파일이면 s3 URI를 돌려주고 태그는 건드리지 않는다")
  void verifyReturnsUriWithoutTouchingTag() {
    given(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .willReturn(HeadObjectResponse.builder().contentLength(2048L).build());

    String result = storage.verify("ad-history/abc.csv");

    assertThat(result).isEqualTo("s3://test-bucket/ad-history/abc.csv");
    then(s3Client).should(never())
        .putObjectTagging(ArgumentMatchers.<Consumer<PutObjectTaggingRequest.Builder>>any());
  }

  @Test
  @DisplayName("confirm()은 삭제 방지 태그를 지운다")
  void confirmClearsTag() {
    storage.confirm("ad-history/abc.csv");

    then(s3Client).should().putObjectTagging(ArgumentMatchers.<Consumer<PutObjectTaggingRequest.Builder>>any());
  }

  @Test
  @DisplayName("verify()는 10MB를 초과하면 InvalidPerformanceFileException")
  void verifyRejectsOversizedFile() {
    given(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .willReturn(HeadObjectResponse.builder().contentLength(11 * 1024 * 1024L).build());

    assertThatThrownBy(() -> storage.verify("ad-history/abc.csv"))
        .isInstanceOf(InvalidPerformanceFileException.class);
  }

  @Test
  @DisplayName("verify()는 객체가 없으면(404) InvalidPerformanceFileException")
  void verifyRejectsMissingObject() {
    willThrow(S3Exception.builder().statusCode(404).build())
        .given(s3Client).headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any());

    assertThatThrownBy(() -> storage.verify("ad-history/missing.csv"))
        .isInstanceOf(InvalidPerformanceFileException.class);
  }

  @Test
  @DisplayName("verify()는 Content-Length가 없으면 NPE 대신 InvalidPerformanceFileException")
  void verifyRejectsNullContentLength() {
    given(s3Client.headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any()))
        .willReturn(HeadObjectResponse.builder().build());

    assertThatThrownBy(() -> storage.verify("ad-history/abc.csv"))
        .isInstanceOf(InvalidPerformanceFileException.class);
  }

  @Test
  @DisplayName("verify()는 허용되지 않은 확장자를 S3 호출 없이 거부한다")
  void verifyRejectsDisallowedExtension() {
    assertThatThrownBy(() -> storage.verify("ad-history/abc.pdf"))
        .isInstanceOf(InvalidPerformanceFileException.class);
    then(s3Client).should(never()).headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any());
  }

  @Test
  @DisplayName("verify()는 ad-history/ prefix가 아닌 key를 S3 호출 없이 거부한다")
  void verifyRejectsWrongPrefix() {
    assertThatThrownBy(() -> storage.verify("other-bucket-path/abc.xlsx"))
        .isInstanceOf(InvalidPerformanceFileException.class);
    then(s3Client).should(never()).headObject(ArgumentMatchers.<Consumer<HeadObjectRequest.Builder>>any());
  }
}
