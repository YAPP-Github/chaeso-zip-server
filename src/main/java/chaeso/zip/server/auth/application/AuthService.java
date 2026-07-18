package chaeso.zip.server.auth.application;

import chaeso.zip.server.auth.application.dto.GoogleAuthResponse;
import chaeso.zip.server.auth.application.dto.GoogleSignupCommand;
import chaeso.zip.server.auth.application.dto.LoginCommand;
import chaeso.zip.server.auth.application.dto.SignupCommand;
import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.application.dto.UserResponse;
import java.util.UUID;

/**
 * 인증 유스케이스. 회원가입(인증코드 발송·검증·가입), 세션(로그인·재발급·로그아웃), 구글 인증을 제공한다.
 */
public interface AuthService {

  /** 이미 로컬로 가입된 이메일이면 사용하는 안내 코드가 아니라 예외(EMAIL_ALREADY_EXISTS)로 처리된다. */
  String EMAIL_ALREADY_USED_WITH_GOOGLE = "EMAIL_ALREADY_USED_WITH_GOOGLE";

  /**
   * 가입할 이메일로 6자리 인증코드를 발송한다. 로컬로 이미 가입된 이메일이면 예외. 구글로만 가입된
   * 이메일이면(로컬 연결 없음) 예외를 던지지 않고 코드를 발송한 뒤 {@link #EMAIL_ALREADY_USED_WITH_GOOGLE}를
   * 돌려준다. 그 외에는 {@code null}.
   */
  String sendSignupVerificationCode(String email);

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

  /**
   * 구글 idToken 을 검증하고 계정 상태에 따라 분기한다.
   * 1. 구글 계정이 연결 -> 로그인,
   * 2. 같은 이메일의 로컬 계정 존재 -> <b>연결하지 않고</b> 사용자 확인을 요구하며, 신규면 가입 티켓을 발급
   * idToken 이 유효하지 않으면 예외.
   */
  GoogleAuthResponse googleAuth(String idToken);

  /**
   * 사용자가 확인한 뒤 같은 이메일의 로컬 계정에 구글 로그인을 연결하고 토큰을 발급한다.
   * 이미 연결돼 있으면 그대로 두고 토큰만 발급한다. idToken 이 유효하지 않거나 붙을 계정이 없으면 예외.
   */
  TokenResponse linkGoogle(String idToken);

  /**
   * signupToken 이 가리키는 구글 클레임과 추가 프로필로 신규 회원을 완성하고 토큰을 발급한다.
   * 성공하면 signupToken 은 즉시 폐기해 재사용을 막는다. 티켓이 없거나 만료됐으면 예외.
   */
  TokenResponse signupGoogle(GoogleSignupCommand command);
}