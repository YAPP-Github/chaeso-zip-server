package chaeso.zip.server.auth.presentation;

import chaeso.zip.server.auth.application.AuthService;
import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.application.dto.UserResponse;
import chaeso.zip.server.auth.application.dto.GoogleAuthResponse;
import chaeso.zip.server.auth.application.dto.LoginMethodsResponse;
import chaeso.zip.server.auth.presentation.dto.GoogleAuthRequest;
import chaeso.zip.server.auth.presentation.dto.GoogleSignupRequest;
import chaeso.zip.server.auth.presentation.dto.LoginMethodsRequest;
import chaeso.zip.server.auth.presentation.dto.LoginRequest;
import chaeso.zip.server.auth.presentation.dto.RefreshTokenRequest;
import chaeso.zip.server.auth.presentation.dto.SendVerificationCodeRequest;
import chaeso.zip.server.auth.presentation.dto.SignupRequest;
import chaeso.zip.server.auth.presentation.dto.VerifyEmailCodeRequest;
import chaeso.zip.server.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 회원가입/이메일 인증 요청을 처리한다. 문서 정의는 {@link AuthApiDocs}. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApiDocs {

  private final AuthService authService;

  @Override
  @PostMapping("/signup/email-code")
  public ApiResponse<Void> sendSignupCode(
      @Valid @RequestBody SendVerificationCodeRequest request) {
    String code = authService.sendSignupVerificationCode(request.email());
    return ApiResponse.success(null, code);
  }

  @Override
  @PostMapping("/signup/email-code/verify")
  public ApiResponse<Void> verifySignupCode(
      @Valid @RequestBody VerifyEmailCodeRequest request) {
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
  @PostMapping("/login/methods")
  public ApiResponse<LoginMethodsResponse> loginMethods(
      @Valid @RequestBody LoginMethodsRequest request,
      HttpServletRequest httpRequest) {
    return ApiResponse.success(
        authService.findLoginMethods(request.email(), httpRequest.getRemoteAddr()));
  }

  @Override
  @PostMapping("/refresh")
  public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
    return ApiResponse.success(authService.reissue(request.refreshToken()));
  }

  @Override
  @PostMapping("/google")
  public ApiResponse<GoogleAuthResponse> googleAuth(@Valid @RequestBody GoogleAuthRequest request) {
    return ApiResponse.success(authService.googleAuth(request.idToken()));
  }

  @Override
  @PostMapping("/google/link")
  public ApiResponse<TokenResponse> linkGoogle(@Valid @RequestBody GoogleAuthRequest request) {
    return ApiResponse.success(authService.linkGoogle(request.idToken()), GOOGLE_ACCOUNT_LINKED);
  }

  @Override
  @PostMapping("/signup/google")
  public ApiResponse<TokenResponse> signupGoogle(@Valid @RequestBody GoogleSignupRequest request) {
    return ApiResponse.success(authService.signupGoogle(request.toCommand()));
  }

  @Override
  @PostMapping("/logout")
  public ApiResponse<Void> logout(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody RefreshTokenRequest request) {
    authService.logout(principal.userId(), request.refreshToken());
    return ApiResponse.success();
  }
}
