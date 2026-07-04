package chaeso.zip.server.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import chaeso.zip.server.common.exception.BusinessException;
import chaeso.zip.server.common.mail.VerificationMailSender;
import chaeso.zip.server.common.security.EmailVerificationCodeStore;
import chaeso.zip.server.common.security.JwtTokenProvider;
import chaeso.zip.server.common.security.RefreshTokenStore;
import chaeso.zip.server.user.application.dto.LoginCommand;
import chaeso.zip.server.user.application.dto.SignupCommand;
import chaeso.zip.server.user.application.dto.TokenResponse;
import chaeso.zip.server.user.application.dto.UserResponse;
import chaeso.zip.server.user.domain.AuthIdentity;
import chaeso.zip.server.user.domain.AuthIdentityRepository;
import chaeso.zip.server.user.domain.AuthProvider;
import chaeso.zip.server.user.domain.EmploymentStatus;
import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.common.security.RefreshTokenInfo;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserErrorCode;
import chaeso.zip.server.user.domain.UserRepository;
import chaeso.zip.server.common.security.InvalidTokenException;
import java.util.UUID;
import java.sql.SQLException;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Mock
  private UserRepository userRepository;
  @Mock
  private EmailVerificationCodeStore verificationCodeStore;
  @Mock
  private VerificationMailSender verificationMailSender;
  @Mock
  private AuthIdentityRepository authIdentityRepository;
  @Mock
  private PasswordEncoder passwordEncoder;
  @Mock
  private JwtTokenProvider jwtTokenProvider;
  @Mock
  private RefreshTokenStore refreshTokenStore;

  @InjectMocks
  private AuthService authService;

  @Test
  @DisplayName("가입되지 않은 이메일이면 6자리 코드를 생성해 저장하고 메일로 발송한다")
  void sendSignupVerificationCode_success() {
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(false);

    authService.sendSignupVerificationCode("user@chaeso.zip");

    ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    then(verificationCodeStore).should().saveCode(eq("user@chaeso.zip"), codeCaptor.capture());
    then(verificationMailSender).should()
        .sendVerificationCode(eq("user@chaeso.zip"), eq(codeCaptor.getValue()));
    assertThat(codeCaptor.getValue()).matches("\\d{6}");
  }

  @Test
  @DisplayName("이미 가입된 이메일이면 코드를 발송하지 않고 예외가 발생한다")
  void sendSignupVerificationCode_alreadyRegistered() {
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(true);

    assertBusinessError(
        () -> authService.sendSignupVerificationCode("user@chaeso.zip"),
        UserErrorCode.EMAIL_ALREADY_EXISTS);
    then(verificationMailSender).should(never()).sendVerificationCode(any(), any());
  }

  @Test
  @DisplayName("이메일 인증코드 발송 시 이메일을 정규화해 조회/저장/발송에 사용한다")
  void sendSignupVerificationCode_normalizesEmail() {
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(false);

    authService.sendSignupVerificationCode("  User@Chaeso.Zip  ");

    then(userRepository).should().existsByEmailAndDeletedAtIsNull("user@chaeso.zip");
    then(verificationCodeStore).should().saveCode(eq("user@chaeso.zip"), any());
    then(verificationMailSender).should().sendVerificationCode(eq("user@chaeso.zip"), any());
  }

  @Test
  @DisplayName("저장된 코드와 일치하면 인증완료 처리한다")
  void verifySignupCode_success() {
    given(verificationCodeStore.verifyCode("user@chaeso.zip", "123456")).willReturn(true);

    authService.verifySignupCode("user@chaeso.zip", "123456");

    then(verificationCodeStore).should().verifyCode("user@chaeso.zip", "123456");
  }

  @Test
  @DisplayName("코드가 일치하지 않으면 예외가 발생한다")
  void verifySignupCode_mismatch() {
    given(verificationCodeStore.verifyCode("user@chaeso.zip", "000000")).willReturn(false);

    assertBusinessError(
        () -> authService.verifySignupCode("user@chaeso.zip", "000000"),
        UserErrorCode.VERIFICATION_CODE_INVALID);
    then(verificationCodeStore).should().verifyCode("user@chaeso.zip", "000000");
  }

  @Test
  @DisplayName("이메일 인증코드 확인 시 이메일을 정규화해 저장된 코드를 조회한다")
  void verifySignupCode_normalizesEmail() {
    given(verificationCodeStore.verifyCode("user@chaeso.zip", "123456")).willReturn(true);

    authService.verifySignupCode("  User@Chaeso.Zip  ", "123456");

    then(verificationCodeStore).should().verifyCode("user@chaeso.zip", "123456");
  }

  @Test
  @DisplayName("이메일 인증을 마친 뒤 회원가입하면 회원 응답을 반환하고 로컬 로그인 수단을 저장한다")
  void signup_success() {
    given(verificationCodeStore.isVerified("user@chaeso.zip")).willReturn(true);
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(false);
    given(passwordEncoder.encode("rawPw")).willReturn("hashed");
    given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

    UserResponse response = authService.signup(new SignupCommand(
        "user@chaeso.zip",
        "rawPw",
        "채소러버",
        EmploymentStatus.EMPLOYEE,
        "채소컴퍼니",
        Occupation.DEVELOPMENT,
        true,
        "v1.0",
        false));

    assertThat(response.email()).isEqualTo("user@chaeso.zip");
    assertThat(response.nickname()).isEqualTo("채소러버");
    then(authIdentityRepository).should().save(any(AuthIdentity.class));
    then(verificationCodeStore).should().clearVerified("user@chaeso.zip");
  }

  @Test
  @DisplayName("이메일 인증을 완료하지 않았으면 회원가입 시 예외가 발생하고 저장하지 않는다")
  void signup_emailNotVerified() {
    given(verificationCodeStore.isVerified("user@chaeso.zip")).willReturn(false);

    assertBusinessError(() -> authService.signup(new SignupCommand(
        "user@chaeso.zip",
        "rawPw",
        "닉",
        EmploymentStatus.EMPLOYEE,
        null,
        null,
        true,
        "v1.0",
        false)), UserErrorCode.EMAIL_NOT_VERIFIED);
    then(userRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("이미 가입된 이메일이면 회원가입 시 예외가 발생한다")
  void signup_duplicateEmail() {
    given(verificationCodeStore.isVerified("user@chaeso.zip")).willReturn(true);
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(true);

    assertBusinessError(() -> authService.signup(new SignupCommand(
        "user@chaeso.zip",
        "rawPw",
        "닉",
        EmploymentStatus.EMPLOYEE,
        null,
        null,
        true,
        "v1.0",
        false)), UserErrorCode.EMAIL_ALREADY_EXISTS);
  }

  @Test
  @DisplayName("동시 가입으로 DB 이메일 unique 제약이 충돌하면 이메일 중복 예외로 변환한다")
  void signup_databaseDuplicateEmail() {
    given(verificationCodeStore.isVerified("user@chaeso.zip")).willReturn(true);
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(false);
    given(userRepository.saveAndFlush(any(User.class)))
        .willThrow(new DataIntegrityViolationException("duplicate email",
            new ConstraintViolationException(
                "duplicate email", new SQLException("duplicate", "23505"), "uq_users_email_active")));

    assertBusinessError(() -> authService.signup(signupCommand()), UserErrorCode.EMAIL_ALREADY_EXISTS);
  }

  @Test
  @DisplayName("인증 상태 정리에 실패해도 완료된 회원가입은 실패 처리하지 않는다")
  void signup_verificationCleanupFailureIgnored() {
    given(verificationCodeStore.isVerified("user@chaeso.zip")).willReturn(true);
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(false);
    given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
    given(passwordEncoder.encode("rawPw")).willReturn("hashed");
    willThrow(new IllegalStateException("redis unavailable"))
        .given(verificationCodeStore).clearVerified("user@chaeso.zip");

    assertThatCode(() -> authService.signup(signupCommand())).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("로그인에 성공하면 access/refresh 토큰을 발급하고 마지막 로그인 기록을 남긴다")
  void login_success() {
    User user = User.create(
        "user@chaeso.zip",
        "채소러버",
        EmploymentStatus.EMPLOYEE,
        null,
        Occupation.DEVELOPMENT,
        true,
        "v1.0",
        false);
    AuthIdentity identity = AuthIdentity.createLocal(user.getId(), "hashed");
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(Optional.of(user));
    given(authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL))
        .willReturn(Optional.of(identity));
    given(passwordEncoder.matches("rawPw", "hashed")).willReturn(true);
    given(jwtTokenProvider.createAccessToken(any())).willReturn("access");
    given(jwtTokenProvider.createRefreshToken(any(), anyString(), anyString())).willReturn("refresh");

    TokenResponse response = authService.login(new LoginCommand("user@chaeso.zip", "rawPw"));

    assertThat(response.accessToken()).isEqualTo("access");
    assertThat(response.refreshToken()).isEqualTo("refresh");
    assertThat(user.getLastLoginAt()).isNotNull();
    assertThat(user.getLastLoginProvider()).isEqualTo(AuthProvider.LOCAL);
    ArgumentCaptor<String> familyIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> jtiCaptor = ArgumentCaptor.forClass(String.class);
    then(refreshTokenStore).should()
        .save(eq(user.getId()), familyIdCaptor.capture(), jtiCaptor.capture());
    then(jwtTokenProvider).should().createRefreshToken(
        eq(user.getId()), eq(familyIdCaptor.getValue()), eq(jtiCaptor.getValue()));
  }

  @Test
  @DisplayName("비밀번호가 일치하지 않으면 로그인 시 인증 예외가 발생한다")
  void login_wrongPassword() {
    User user = User.create(
        "user@chaeso.zip",
        "닉",
        EmploymentStatus.EMPLOYEE,
        null,
        null,
        true,
        "v1.0",
        false);
    AuthIdentity identity = AuthIdentity.createLocal(user.getId(), "hashed");
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(Optional.of(user));
    given(authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL))
        .willReturn(Optional.of(identity));
    given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

    assertBusinessError(
        () -> authService.login(new LoginCommand("user@chaeso.zip", "wrong")),
        UserErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("존재하지 않는 이메일이면 로그인 시 인증 예외가 발생한다")
  void login_noSuchEmail() {
    given(userRepository.findByEmailAndDeletedAtIsNull("none@chaeso.zip")).willReturn(Optional.empty());

    assertBusinessError(
        () -> authService.login(new LoginCommand("none@chaeso.zip", "rawPw")),
        UserErrorCode.INVALID_CREDENTIALS);
  }

  private SignupCommand signupCommand() {
    return new SignupCommand(
        "user@chaeso.zip",
        "rawPw",
        "채소러버",
        EmploymentStatus.EMPLOYEE,
        null,
        null,
        true,
        "v1.0",
        false);
  }

  private void assertBusinessError(ThrowingCallable callable, UserErrorCode expectedErrorCode) {
    assertThatThrownBy(callable)
        .isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
  }

  @Test
  @DisplayName("유효한 refresh면 회전하여 새 토큰을 발급한다")
  void reissue_success() {
    User user = User.create("user@chaeso.zip", "닉", EmploymentStatus.EMPLOYEE, null, null, true, "v1.0", false);
    given(jwtTokenProvider.parseRefresh("refresh")).willReturn(new RefreshTokenInfo(USER_ID, "fam-1", "jti-1"));
    given(refreshTokenStore.findJti(USER_ID, "fam-1")).willReturn(Optional.of("jti-1"));
    given(refreshTokenStore.rotate(eq(USER_ID), eq("fam-1"), eq("jti-1"), anyString()))
        .willReturn(RefreshTokenStore.RotateResult.ROTATED);
    given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(Optional.of(user));
    given(jwtTokenProvider.createAccessToken(any())).willReturn("new-access");
    given(jwtTokenProvider.createRefreshToken(any(), anyString(), anyString())).willReturn("new-refresh");

    TokenResponse response = authService.reissue("refresh");

    assertThat(response.accessToken()).isEqualTo("new-access");
    assertThat(response.refreshToken()).isEqualTo("new-refresh");
    then(refreshTokenStore).should().rotate(eq(USER_ID), eq("fam-1"), eq("jti-1"), anyString());
  }

  @Test
  @DisplayName("새 토큰 생성에 실패하면 refresh 상태를 회전하지 않는다")
  void reissue_tokenCreationFails_doesNotRotate() {
    User user = User.create("user@chaeso.zip", "닉", EmploymentStatus.EMPLOYEE, null, null, true, "v1.0", false);
    given(jwtTokenProvider.parseRefresh("refresh")).willReturn(new RefreshTokenInfo(USER_ID, "fam-1", "jti-1"));
    given(refreshTokenStore.findJti(USER_ID, "fam-1")).willReturn(Optional.of("jti-1"));
    given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(Optional.of(user));
    given(jwtTokenProvider.createAccessToken(USER_ID)).willThrow(new IllegalStateException("signing failed"));

    assertThatThrownBy(() -> authService.reissue("refresh"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("signing failed");

    then(refreshTokenStore).should(never()).rotate(any(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("탈퇴한 사용자는 refresh 토큰을 재발급할 수 없다")
  void reissue_deletedUser_rejected() {
    given(jwtTokenProvider.parseRefresh("refresh"))
        .willReturn(new RefreshTokenInfo(USER_ID, "fam-1", "jti-1"));
    given(refreshTokenStore.findJti(USER_ID, "fam-1")).willReturn(Optional.of("jti-1"));
    given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(Optional.empty());

    assertBusinessError(
        () -> authService.reissue("refresh"),
        UserErrorCode.INVALID_REFRESH_TOKEN);

    then(refreshTokenStore).should(never()).rotate(any(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("저장된 jti와 다르면(재사용) 유저 전체 세션을 무효화하고 예외를 던진다")
  void reissue_reuseDetected() {
    given(jwtTokenProvider.parseRefresh("stale")).willReturn(new RefreshTokenInfo(USER_ID, "fam-1", "jti-old"));
    given(refreshTokenStore.findJti(USER_ID, "fam-1")).willReturn(Optional.of("jti-current"));

    assertThatThrownBy(() -> authService.reissue("stale"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(UserErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
    then(refreshTokenStore).should().deleteAllForUser(USER_ID);
  }

  @Test
  @DisplayName("Redis에 family가 없으면(만료/로그아웃) 예외를 던진다")
  void reissue_missingFamily() {
    given(jwtTokenProvider.parseRefresh("refresh")).willReturn(new RefreshTokenInfo(USER_ID, "fam-1", "jti-1"));
    given(refreshTokenStore.findJti(USER_ID, "fam-1")).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.reissue("refresh")).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("서명이 깨진 refresh면 INVALID 예외를 던진다")
  void reissue_invalidToken() {
    given(jwtTokenProvider.parseRefresh("bad")).willThrow(new InvalidTokenException("invalid"));

    assertThatThrownBy(() -> authService.reissue("bad")).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("로그아웃하면 해당 family를 삭제한다")
  void logout_deletesFamily() {
    given(jwtTokenProvider.parseRefresh("refresh")).willReturn(new RefreshTokenInfo(USER_ID, "fam-1", "jti-1"));

    authService.logout("refresh");

    then(refreshTokenStore).should().deleteFamily(USER_ID, "fam-1");
  }

  @Test
  @DisplayName("이미 무효한 토큰으로 로그아웃하면 멱등하게 무시한다")
  void logout_invalidTokenIgnored() {
    given(jwtTokenProvider.parseRefresh("bad")).willThrow(new InvalidTokenException("invalid"));

    authService.logout("bad");

    then(refreshTokenStore).should(never()).deleteFamily(any(), anyString());
  }
}
