package chaeso.zip.server.onboarding.application.dto;

/**
 * 성과 파일 presigned URL 발급 유스케이스의 입력 커맨드.
 */
public record PresignPerformanceFileCommand(String fileName, long fileSizeBytes) {
}
