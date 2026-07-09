package chaeso.zip.server.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "이메일 인증 코드 확인 요청")
public record VerifyEmailCodeRequest(
    @Schema(description = "이메일", example = "user@chaeso.zip", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Email String email,
    @Schema(description = "6자리 인증 코드", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Pattern(regexp = "\\d{6}", message = "인증 코드는 6자리 숫자로 입력해 주세요") String code) {
}
