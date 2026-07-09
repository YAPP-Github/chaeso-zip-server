package chaeso.zip.server.auth.presentation.dto;

import chaeso.zip.server.auth.application.dto.SignupCommand;
import chaeso.zip.server.user.domain.Occupation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 형식 검증은 Bean Validation 으로 수행하고 실패 시 공통 C-001 + fieldErrors 로 반환된다.
 */
@Schema(description = "회원가입 요청 (이메일 인증 완료 후 제출)")
public record SignupRequest(
    @Schema(description = "이메일(사전 인증 완료 필요)", example = "user@chaeso.zip", maxLength = 255, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Email @Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요") String email,

    @Schema(description = "비밀번호(8~64자, 영·숫자·특수문자 각 1자 이상)", example = "P@ssw0rd!", minLength = 8, maxLength = 64, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해 주세요")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$",
        message = "비밀번호는 영어, 숫자, 특수문자를 각각 1개 이상 포함해 주세요")
    String password,

    @Schema(description = "닉네임(이름)", example = "채소러버", maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 50, message = "이름은 50자 이하로 입력해 주세요") String nickname,

    @Schema(description = "회사명(필수, 50자 이하)", example = "채소컴퍼니", maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 50, message = "회사명은 50자 이하로 입력해 주세요") String companyName,

    @Schema(description = "직무 대분류(선택)", example = "DEVELOPMENT", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    Occupation occupation,

    @Schema(description = "필수 약관 동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @AssertTrue(message = "필수 약관에 동의해 주세요") boolean termsAgreed,

    @Schema(description = "마케팅 수신 동의 여부(선택)", example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    boolean marketingAgreed) {

  public SignupCommand toCommand() {
    return new SignupCommand(email, password, nickname, companyName, occupation, termsAgreed,
        marketingAgreed);
  }
}
