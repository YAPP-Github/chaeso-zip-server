package chaeso.zip.server.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 또는 로그아웃 요청")
public record RefreshTokenRequest(
    @Schema(description = "Refresh 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank String refreshToken) {
}
