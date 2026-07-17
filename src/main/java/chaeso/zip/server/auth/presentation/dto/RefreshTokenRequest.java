package chaeso.zip.server.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 재발급/로그아웃 공용 요청 DTO */
@Schema(description = "Refresh Token 요청")
public record RefreshTokenRequest(
    @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank String refreshToken) {
}
