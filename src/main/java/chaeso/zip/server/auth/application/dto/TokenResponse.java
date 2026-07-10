package chaeso.zip.server.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 로그인 성공 시 발급되는 토큰 응답 DTO. 도메인 엔티티를 외부로 직접 노출하지 않기 위한 변환 객체.
 */
@Schema(description = "토큰 응답")
public record TokenResponse(
    @Schema(description = "액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    String accessToken,
    @Schema(description = "리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
    String refreshToken,
    @Schema(description = "액세스 토큰 만료(초)", example = "1800", requiredMode = Schema.RequiredMode.REQUIRED)
    long accessTokenExpiresIn,
    @Schema(description = "리프레시 토큰 만료(초)", example = "1209600", requiredMode = Schema.RequiredMode.REQUIRED)
    long refreshTokenExpiresIn) {

}
