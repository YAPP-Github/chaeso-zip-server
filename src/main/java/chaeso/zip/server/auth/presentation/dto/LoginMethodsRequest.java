package chaeso.zip.server.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 수단 조회 요청 DTO.
 */
@Schema(description = "로그인 수단 조회 요청")
public record LoginMethodsRequest(
    @Schema(description = "조회할 이메일", example = "user@chaeso.zip",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Email String email) {
}
