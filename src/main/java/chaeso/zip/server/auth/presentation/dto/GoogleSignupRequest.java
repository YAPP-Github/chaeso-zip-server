package chaeso.zip.server.auth.presentation.dto;

import chaeso.zip.server.auth.application.dto.GoogleSignupCommand;
import chaeso.zip.server.user.domain.Occupation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
* 구글 최종 회원가입 요청 DTO. 필드 검증은 표현 계층에서 수행하고 {@link #toCommand()} 로 애플리케이션 커맨드로 변환한다.
*/
@Schema(description = "구글 최종 회원가입 요청 (signupToken + 로컬 가입과 동일 필드, password 제외)")
public record GoogleSignupRequest(
    @Schema(description = "구글 인증 진입에서 받은 일회성 가입 티켓", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "signupToken을 입력해 주세요") String signupToken,

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

  public GoogleSignupCommand toCommand() {
    return new GoogleSignupCommand(signupToken, nickname, companyName, occupation, termsAgreed,
        marketingAgreed);
  }
}
