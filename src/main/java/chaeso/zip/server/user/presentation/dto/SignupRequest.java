package chaeso.zip.server.user.presentation.dto;

import chaeso.zip.server.user.application.dto.SignupCommand;
import chaeso.zip.server.user.domain.EmploymentStatus;
import chaeso.zip.server.user.domain.Occupation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record SignupRequest(
    @Schema(description = "이메일(사전에 인증 완료되어야 함)", example = "user@chaeso.zip", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Email String email,
    @Schema(description = "비밀번호 (8자 이상 64자 이하)", example = "P@ssw0rd!", minLength = 8, maxLength = 64, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(min = 8, max = 64) String password,
    @Schema(description = "닉네임", example = "채소러버", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 50) String nickname,
    @Schema(description = "고용 상태", example = "EMPLOYEE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull EmploymentStatus employmentStatus,
    @Schema(description = "회사/학교 이름(선택)", example = "채소컴퍼니", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Size(max = 255) String companyName,
    @Schema(description = "직무 대분류(선택)", example = "DEVELOPMENT", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    Occupation occupation,
    @Schema(description = "필수 약관 동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @AssertTrue(message = "필수 약관에 동의해야 합니다.") boolean termsAgreed,
    @Schema(description = "동의한 약관 버전", example = "v1.0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 20) String termsVersion,
    @Schema(description = "마케팅 수신 동의 여부(선택)", example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    boolean marketingAgreed) {

  public SignupCommand toCommand() {
    return new SignupCommand(email, password, nickname, employmentStatus, companyName, occupation,
        termsAgreed, termsVersion, marketingAgreed);
  }
}
