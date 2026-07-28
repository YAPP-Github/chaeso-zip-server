package chaeso.zip.server.onboarding.infrastructure.s3;

import chaeso.zip.server.onboarding.application.InvalidPerformanceFileException;
import chaeso.zip.server.onboarding.application.PerformanceFileStorage;
import chaeso.zip.server.onboarding.application.dto.PresignPerformanceFileCommand;
import chaeso.zip.server.onboarding.application.dto.PresignedFileUploadResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * AWS S3에 성과파일을 업로드하고 확인하는 저장소 구현체.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3PerformanceFileStorage implements PerformanceFileStorage {

  private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

  /** 확장자별 Content-Type. */
  private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
      ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      ".csv", "text/csv");

  /** presign 시점에 붙이는 임시 태그. 제출하지 않거나 비로그인일 경우 S3 lifecycle이 1일 후 삭제. */
  private static final String PENDING_TAGGING = "retain=pending";

  private static final String KEY_PREFIX = "ad-history/";

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final OnboardingS3Properties properties;

  @Override
  public List<PresignedFileUploadResult> presign(List<PresignPerformanceFileCommand> files) {
    return files.stream().map(this::presignOne).toList();
  }

  /** 파일 1건에 UUID 기반 key를 붙여 presigned PUT URL 생성. */
  private PresignedFileUploadResult presignOne(PresignPerformanceFileCommand file) {
    String extension = extensionOf(file.fileName());
    String contentType = CONTENT_TYPE_BY_EXTENSION.get(extension);
    String key = KEY_PREFIX + UUID.randomUUID() + extension;
    PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest -> presignRequest
        .signatureDuration(properties.presignTtl())
        .putObjectRequest(objectRequest -> objectRequest
            .bucket(properties.bucket())
            .key(key)
            .contentType(contentType)
            .contentLength(file.fileSizeBytes())
            .tagging(PENDING_TAGGING)));
    return new PresignedFileUploadResult(key, presigned.url().toString(), contentType,
        presigned.expiration());
  }

  @Override
  public String verify(String key) {
    if (!key.startsWith(KEY_PREFIX)) {
      log.warn("허용되지 않은 성과파일 경로입니다. key={}", key);
      throw new InvalidPerformanceFileException("허용되지 않은 성과파일 경로입니다. key=" + key);
    }
    if (!CONTENT_TYPE_BY_EXTENSION.containsKey(extensionOf(key))) {
      log.warn("지원하지 않는 성과파일 형식입니다. key={}", key);
      throw new InvalidPerformanceFileException("지원하지 않는 성과파일 형식입니다. key=" + key);
    }

    HeadObjectResponse head;
    try {
      head = s3Client.headObject(request -> request.bucket(properties.bucket()).key(key));
    } catch (S3Exception e) {
      if (e.statusCode() == 404 || e.statusCode() == 403) {
        log.warn("존재하지 않거나 접근할 수 없는 성과파일입니다. key={}, statusCode={}", key,
            e.statusCode(), e);
        throw new InvalidPerformanceFileException("존재하지 않거나 접근할 수 없는 성과파일입니다. key=" + key, e);
      }
      throw e;
    }

    if (head.contentLength() == null || head.contentLength() > MAX_FILE_SIZE_BYTES) {
      log.warn("성과파일 크기 조건을 만족하지 않습니다. key={}, contentLength={}", key,
          head.contentLength());
      throw new InvalidPerformanceFileException("성과파일 크기 조건을 만족하지 않습니다. key=" + key);
    }

    return "s3://" + properties.bucket() + "/" + key;
  }

  @Override
  public void confirm(String key) {
    s3Client.putObjectTagging(request -> request
        .bucket(properties.bucket())
        .key(key)
        .tagging(tagging -> tagging.tagSet(List.of())));
  }

  private static String extensionOf(String fileNameOrKey) {
    int dot = fileNameOrKey.lastIndexOf('.');
    return dot < 0 ? "" : fileNameOrKey.substring(dot).toLowerCase(Locale.ROOT);
  }
}
