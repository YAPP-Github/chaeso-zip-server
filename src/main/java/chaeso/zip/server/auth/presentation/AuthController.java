package chaeso.zip.server.auth.presentation;

import chaeso.zip.server.auth.application.AuthService;
import chaeso.zip.server.auth.application.dto.UserResponse;
import chaeso.zip.server.auth.presentation.dto.SendVerificationCodeRequest;
import chaeso.zip.server.auth.presentation.dto.SignupRequest;
import chaeso.zip.server.auth.presentation.dto.VerifyEmailCodeRequest;
import chaeso.zip.server.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    authService.sendSignupVerificationCode(request.email());
    return ApiResponse.success();
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
}
