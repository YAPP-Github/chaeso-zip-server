package chaeso.zip.server.user.presentation;

import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.user.application.dto.TokenResponse;
import chaeso.zip.server.user.application.dto.UserResponse;
import chaeso.zip.server.user.presentation.dto.LoginRequest;
import chaeso.zip.server.user.presentation.dto.RefreshTokenRequest;
import chaeso.zip.server.user.presentation.dto.SendVerificationCodeRequest;
import chaeso.zip.server.user.presentation.dto.SignupRequest;
import chaeso.zip.server.user.presentation.dto.VerifyEmailCodeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 인증 API의 OpenAPI 명세를 정의한다.
 *
 * <p>이 인터페이스의 API에는 전역 Bearer 인증 요구사항을 적용하지 않는다.
 */
@Tag(name = "Auth", description = "인증 API")
@SecurityRequirements
public interface AuthApiDocs {

  @Operation(operationId = "authSendSignupCode", summary = "이메일 인증 코드 발송",
      description = "회원가입을 위한 6자리 인증 코드를 이메일로 발송한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발송 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  ApiResponse<Void> sendSignupCode(SendVerificationCodeRequest request);

  @Operation(operationId = "authVerifySignupCode", summary = "이메일 인증 코드 확인",
      description = "발송된 인증 코드를 확인해 이메일 인증을 완료한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 유효하지 않은 인증 코드",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  ApiResponse<Void> verifySignupCode(VerifyEmailCodeRequest request);

  @Operation(operationId = "authSignup", summary = "회원가입",
      description = "이메일 인증을 완료한 뒤 이메일/비밀번호로 회원가입한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "가입 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 이메일 미인증",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  ApiResponse<UserResponse> signup(SignupRequest request);

  @Operation(operationId = "authLogin", summary = "로그인", description = "이메일/비밀번호로 로그인하고 토큰을 발급한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일/비밀번호 불일치",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  ApiResponse<TokenResponse> login(LoginRequest request);

  @Operation(operationId = "authReissue", summary = "토큰 재발급", description = "refresh 토큰으로 access/refresh 를 회전 발급한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 토큰 또는 토큰 재사용 감지",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  ApiResponse<TokenResponse> reissue(RefreshTokenRequest request);

  @Operation(operationId = "authLogout", summary = "로그아웃", description = "해당 세션(refresh family)을 무효화한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  ApiResponse<Void> logout(RefreshTokenRequest request);
}
