package chaeso.zip.server.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "이메일 인증 코드 발송 요청")
public record SendVerificationCodeRequest(
    @Schema(description = "이메일", example = "user@chaeso.zip", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Email String email) {
}
