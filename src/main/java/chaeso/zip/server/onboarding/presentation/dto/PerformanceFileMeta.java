package chaeso.zip.server.onboarding.presentation.dto;

import chaeso.zip.server.onboarding.application.dto.PresignPerformanceFileCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * presign 발급할 성과파일 1건의 메타데이터 요청 DTO.
 */
@Schema(description = "presign 발급할 성과파일 1건의 메타데이터")
public record PerformanceFileMeta(
    @Schema(description = "원본 파일명 (확장자 포함)", example = "3월_집행실적.xlsx",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Pattern(regexp = "(?i).+\\.(xlsx|csv)$", message = "파일은 xlsx 또는 csv만 첨부할 수 있습니다")
    String fileName,

    @Schema(description = "파일 크기(바이트, 최대 10MB)", example = "1048576",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @Positive @Max(10 * 1024 * 1024) long fileSizeBytes) {

  public PresignPerformanceFileCommand toCommand() {
    return new PresignPerformanceFileCommand(fileName, fileSizeBytes);
  }
}
