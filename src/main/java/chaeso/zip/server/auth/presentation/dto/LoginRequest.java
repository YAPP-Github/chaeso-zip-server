package chaeso.zip.server.auth.presentation.dto;

import chaeso.zip.server.auth.application.dto.LoginCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로컬 로그인 요청 DTO. 형식 검증은 표현 계층에서 수행하고 {@link #toCommand()} 로 애플리케이션 커맨드로 변환한다.
 */
@Schema(description = "로컬 로그인 요청")
public record LoginRequest(
    @Schema(description = "이메일", example = "user@chaeso.zip", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Email String email,

    @Schema(description = "비밀번호", example = "P@ssw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank String password) {

  public LoginCommand toCommand() {
    return new LoginCommand(email, password);
  }
}
