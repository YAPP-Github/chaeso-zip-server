package chaeso.zip.server.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 구글 인증 진입({@code POST /auth/google}) 응답. 세 분기(로그인/연결 확인 필요/가입 필요) {@code status} 중 한 상태를 보여주고
 * 해당 분기에 없는 필드는 null 로 내려간다.
 */
@Schema(description = "구글 인증 진입 응답")
public record GoogleAuthResponse(
    @Schema(description = "분기 판별값", requiredMode = Schema.RequiredMode.REQUIRED) Status status,
    @Schema(description = "액세스 토큰. 로그인 분기에만 존재",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String accessToken,
    @Schema(description = "리프레시 토큰. 로그인 분기에만 존재",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String refreshToken,
    @Schema(description = "액세스 토큰 만료(초)", example = "1800",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long accessTokenExpiresIn,
    @Schema(description = "리프레시 토큰 만료(초, 고정값X)", example = "1209600",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long refreshTokenExpiresIn,

    @Schema(description = "같은 이메일의 로컬 계정이 있어 연결 확인이 필요함. 아직 연결되지 않았다", example = "true",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Boolean linkRequired,
    @Schema(description = "연결 대상 계정의 이메일. 어느 계정에 붙는지 사용자가 보고 판단하도록 내려준다",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String email,

    @Schema(description = "가입 이력이 없어 추가정보 입력이 필요함", example = "true",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Boolean signupRequired,
    @Schema(description = "최종가입에 되돌려줄 일회성 티켓",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String signupToken,
    @Schema(description = "최종가입 폼 프리필 값",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Prefill prefill) {

  public enum Status {
    LOGIN, LINK_REQUIRED, SIGNUP_REQUIRED
  }

  @Schema(description = "가입 폼 프리필")
  public record Prefill(
      @Schema(description = "구글이 인증한 이메일", example = "user@chaeso.zip",
          requiredMode = Schema.RequiredMode.REQUIRED) String email,
      @Schema(description = "구글 계정 이름. 계정에 이름이 없으면 null", example = "홍길동",
          requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
      String suggestedNickname) {
  }

  public static GoogleAuthResponse login(TokenResponse token) {
    return new GoogleAuthResponse(Status.LOGIN, token.accessToken(), token.refreshToken(),
        token.accessTokenExpiresIn(), token.refreshTokenExpiresIn(),
        null, null, null, null, null);
  }

  public static GoogleAuthResponse linkRequired(String email) {
    return new GoogleAuthResponse(Status.LINK_REQUIRED, null, null, null, null, true, email, null,
        null, null);
  }

  public static GoogleAuthResponse signupRequired(String signupToken, String email,
      String suggestedNickname) {
    return new GoogleAuthResponse(Status.SIGNUP_REQUIRED, null, null, null, null, null, null, true,
        signupToken, new Prefill(email, suggestedNickname));
  }
}
