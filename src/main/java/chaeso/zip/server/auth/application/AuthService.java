package chaeso.zip.server.auth.application;

import chaeso.zip.server.auth.application.dto.LoginCommand;
import chaeso.zip.server.auth.application.dto.SignupCommand;
import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.application.dto.UserResponse;

/**
 * 로컬 회원가입 유스케이스. 인증코드 발송, 검증, 최종 가입의 3단계를 제공한다.
 */
public interface AuthService {

  /** 가입할 이메일로 6자리 인증코드를 발송한다. 이미 가입된 이메일이면 예외. */
  void sendSignupVerificationCode(String email);

  /** 인증코드를 검증하고 인증완료 상태로 전환한다. 불일치/만료면 예외. */
  void verifySignupCode(String email, String code);

  /** 이메일 인증 완료를 전제로 로컬 계정을 생성한다. */
  UserResponse signup(SignupCommand command);

  /** 이메일/비밀번호로 로컬 로그인하고 액세스/리프레시 토큰을 발급한다. 실패 시 예외. */
  TokenResponse login(LoginCommand command);
}