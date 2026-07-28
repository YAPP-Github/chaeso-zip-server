package chaeso.zip.server.onboarding.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 성과파일 presigned URL 발급 요청 DTO.
 */
@Schema(description = "성과파일 presigned URL 발급 요청")
public record PresignPerformanceFilesRequest(
    @Schema(description = "발급할 파일 목록. 최대 5개", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty @Size(max = 5) @Valid List<PerformanceFileMeta> files) {
}
