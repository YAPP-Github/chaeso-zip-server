package chaeso.zip.server.onboarding.application;

import chaeso.zip.server.onboarding.application.dto.PresignPerformanceFileCommand;
import chaeso.zip.server.onboarding.application.dto.PresignedFileUploadResult;
import java.util.List;

/**
 * 성과 파일 저장소 인터페이스.
 */
public interface PerformanceFileStorage {

  /**
   * 성과 파일 업로드용 Presigned URL 목록을 발급한다.
   */
  List<PresignedFileUploadResult> presign(List<PresignPerformanceFileCommand> files);

  /**
   * 제출된 성과파일이 유효한지 확인한다.
   *
   * @param key 파일 식별자
   * @return 파일의 URL (예: s3://bucket/key)
   * @throws InvalidPerformanceFileException key 경로/확장자가 허용되지 않거나, 파일이 없거나, 크기 조건을 만족하지 않는 경우
   */
  String verify(String key);

  /**
   * 성과파일의 삭제 방지 태그를 지운다.
   *
   * @param key 파일 식별자
   */
  void confirm(String key);
}
