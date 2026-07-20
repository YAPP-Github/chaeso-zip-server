package chaeso.zip.server.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 구글 idToken 요청 DTO. 필드 검증은 표현 계층에서 수행하고, 커맨드 없이 idToken 값만 애플리케이션 계층에 전달한다.
 */
@Schema(description = "구글 idToken 요청")
public record GoogleAuthRequest(
    @Schema(description = "Google Identity Services 로 받은 idToken", example = "eyJhbGciOiJSUzI1NiJ9...",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "idToken을 입력해 주세요") String idToken) {
}
