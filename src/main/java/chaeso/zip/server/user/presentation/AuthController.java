package chaeso.zip.server.user.presentation;

import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.user.application.AuthService;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입, 로그인, 로그아웃 및 토큰 재발급 등 인증 관련 요청을 처리하는 컨트롤러.
 */
@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @Operation(operationId = "authSendSignupCode", summary = "이메일 인증 코드 발송",
      description = "회원가입을 위한 6자리 인증 코드를 이메일로 발송한다.")
  @SecurityRequirement(name = "")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발송 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 이미 가입된 이메일",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  @PostMapping("/signup/email-code")
  public ApiResponse<Void> sendSignupCode(@Valid @RequestBody SendVerificationCodeRequest request) {
    authService.sendSignupVerificationCode(request.email());
    return ApiResponse.success();
  }

  @Operation(operationId = "authVerifySignupCode", summary = "이메일 인증 코드 확인",
      description = "발송된 인증 코드를 확인해 이메일 인증을 완료한다.")
  @SecurityRequirement(name = "")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 유효하지 않은 인증 코드",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  @PostMapping("/signup/email-code/verify")
  public ApiResponse<Void> verifySignupCode(@Valid @RequestBody VerifyEmailCodeRequest request) {
    authService.verifySignupCode(request.email(), request.code());
    return ApiResponse.success();
  }

  @Operation(operationId = "authSignup", summary = "회원가입",
      description = "이메일 인증을 완료한 뒤 이메일/비밀번호로 회원가입한다.")
  @SecurityRequirement(name = "")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "가입 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 이메일 미인증/중복",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
    return ApiResponse.success(authService.signup(request.toCommand()));
  }

  @Operation(operationId = "authLogin", summary = "로그인", description = "이메일/비밀번호로 로그인하고 토큰을 발급한다.")
  @SecurityRequirement(name = "")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 이메일/비밀번호 불일치",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.success(authService.login(request.toCommand()));
  }

  @Operation(operationId = "authReissue", summary = "토큰 재발급", description = "refresh 토큰으로 access/refresh 를 회전 발급한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 유효하지 않은 토큰/토큰 재사용 감지",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  @PostMapping("/reissue")
  public ApiResponse<TokenResponse> reissue(@Valid @RequestBody RefreshTokenRequest request) {
    return ApiResponse.success(authService.reissue(request.refreshToken()));
  }

  @Operation(operationId = "authLogout", summary = "로그아웃", description = "해당 세션(refresh family)을 무효화한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패",
      content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  @PostMapping("/logout")
  public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
    authService.logout(request.refreshToken());
    return ApiResponse.success();
  }
}
