package chaeso.zip.server.auth.application;

import chaeso.zip.server.auth.application.dto.LoginCommand;
import chaeso.zip.server.auth.application.dto.SignupCommand;
import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.application.dto.UserResponse;
import java.util.UUID;

/**
 * 로컬 인증 유스케이스. 회원가입(인증코드 발송·검증·가입)과 세션(로그인·재발급·로그아웃)을 제공한다.
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

  /** Refresh Token 을 회전시켜 새 토큰 쌍을 발급한다. familyId 는 유지하고 jti 만 교체한다. 재사용이 탐지되면 family 를 폐기하고 예외. */
  TokenResponse reissue(String refreshToken);

  /** 인증된 {@code userId} 소유의 refresh 토큰이면 family 세션을 폐기한다. 없는 세션은 통과, 소유자가 다르면 예외. */
  void logout(UUID userId, String refreshToken);
}