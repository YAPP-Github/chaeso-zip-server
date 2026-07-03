package chaeso.zip.server.user.presentation.dto;

import chaeso.zip.server.user.application.dto.LoginCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public record LoginRequest(
    @Schema(description = "이메일", example = "user@chaeso.zip", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Email String email,
    @Schema(description = "비밀번호", example = "P@ssw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank String password) {

  public LoginCommand toCommand() {
    return new LoginCommand(email, password);
  }
}
