package chaeso.zip.server.user.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 응답")
public record TokenResponse(
    @Schema(description = "Access 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
    String accessToken,
    @Schema(description = "Refresh 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
    String refreshToken) {
}
