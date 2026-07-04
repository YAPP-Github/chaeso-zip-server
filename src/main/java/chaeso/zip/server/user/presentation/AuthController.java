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
 * Swagger 문서 정의는 {@link AuthApiDocs} 참고.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApiDocs {

  private final AuthService authService;

  @Override
  @PostMapping("/signup/email-code")
  public ApiResponse<Void> sendSignupCode(@Valid @RequestBody SendVerificationCodeRequest request) {
    authService.sendSignupVerificationCode(request.email());
    return ApiResponse.success();
  }

  @Override
  @PostMapping("/signup/email-code/verify")
  public ApiResponse<Void> verifySignupCode(@Valid @RequestBody VerifyEmailCodeRequest request) {
    authService.verifySignupCode(request.email(), request.code());
    return ApiResponse.success();
  }

  @Override
  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
    return ApiResponse.success(authService.signup(request.toCommand()));
  }

  @Override
  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.success(authService.login(request.toCommand()));
  }

  @Override
  @PostMapping("/reissue")
  public ApiResponse<TokenResponse> reissue(@Valid @RequestBody RefreshTokenRequest request) {
    return ApiResponse.success(authService.reissue(request.refreshToken()));
  }

  @Override
  @PostMapping("/logout")
  public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
    authService.logout(request.refreshToken());
    return ApiResponse.success();
  }
}
