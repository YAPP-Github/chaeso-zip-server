package chaeso.zip.server.onboarding.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "성과파일 1건에 대한 presigned URL 발급 결과")
public record PresignedFileUploadResult(
    @Schema(description = "발급된 S3 object key", example = "ad-history/3f9c1e2a-....xlsx",
        requiredMode = Schema.RequiredMode.REQUIRED)
    String key,
    @Schema(description = "PUT 업로드용 presigned URL", requiredMode = Schema.RequiredMode.REQUIRED)
    String uploadUrl,
    @Schema(description = "PUT 요청의 Content-Type 헤더에 사용할 값",
        example = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        requiredMode = Schema.RequiredMode.REQUIRED)
    String contentType,
    @Schema(description = "presigned URL 만료 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    Instant expiresAt) {
}
