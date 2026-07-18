package chaeso.zip.server.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import chaeso.zip.server.auth.application.dto.GoogleAuthResponse;
import chaeso.zip.server.auth.application.dto.GoogleSignupCommand;
import chaeso.zip.server.auth.application.dto.LoginCommand;
import chaeso.zip.server.auth.application.dto.SignupCommand;
import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.application.dto.UserResponse;
import chaeso.zip.server.auth.domain.AuthBusinessException;
import chaeso.zip.server.auth.domain.AuthErrorCode;
import chaeso.zip.server.auth.domain.AuthIdentity;
import chaeso.zip.server.auth.domain.AuthIdentityRepository;
import chaeso.zip.server.auth.domain.AuthProvider;
import chaeso.zip.server.auth.domain.InvalidTokenException;
import chaeso.zip.server.auth.infrastructure.jwt.JwtProperties;
import chaeso.zip.server.auth.infrastructure.jwt.JwtTokenProvider;
import chaeso.zip.server.auth.infrastructure.jwt.RefreshTokenInfo;
import chaeso.zip.server.auth.infrastructure.jwt.RefreshTokenStore;
import chaeso.zip.server.auth.infrastructure.jwt.RefreshTokenStore.RotateOutcome;
import chaeso.zip.server.auth.infrastructure.jwt.RefreshTokenStore.RotateResult;
import chaeso.zip.server.auth.infrastructure.mail.VerificationMailSender;
import chaeso.zip.server.auth.infrastructure.oauth.GoogleIdTokenInfo;
import chaeso.zip.server.auth.infrastructure.oauth.GoogleIdTokenVerifier;
import chaeso.zip.server.auth.infrastructure.oauth.GoogleSignupStore;
import chaeso.zip.server.auth.infrastructure.verification.EmailVerificationCodeStore;
import chaeso.zip.server.support.UserFixture;
import chaeso.zip.server.user.application.ConsentProperties;
import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 인증 애플리케이션 서비스 단위 테스트. 외부 저장소와 메일 발송은 Mockito 로 대체하고 유스케이스 흐름만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private AuthIdentityRepository authIdentityRepository;

  @Mock
  private EmailVerificationCodeStore verificationCodeStore;

  @Mock
  private VerificationMailSender verificationMailSender;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtTokenProvider jwtTokenProvider;

  @Mock
  private RefreshTokenStore refreshTokenStore;

  @Mock
  private GoogleIdTokenVerifier googleIdTokenVerifier;

  @Mock
  private GoogleSignupStore googleSignupStore;

  private static final JwtProperties JWT_PROPERTIES =
      new JwtProperties("dummy-secret", Duration.ofMinutes(30), Duration.ofDays(14),
          Duration.ofDays(90));

  private AuthServiceImpl authService;

  @BeforeEach
  void setUp() {
    authService = new AuthServiceImpl(
        userRepository,
        authIdentityRepository,
        verificationCodeStore,
        verificationMailSender,
        passwordEncoder,
        new ConsentProperties("v1.0"),
        jwtTokenProvider,
        JWT_PROPERTIES,
        refreshTokenStore,
        googleIdTokenVerifier,
        googleSignupStore);
  }

  private static LoginCommand loginCommand() {
    return new LoginCommand("user@chaeso.zip", "P@ssw0rd!");
  }

  /** 로그인 성공 경로(유저/LOCAL 인증정보 조회, 비밀번호 일치)를 stub 하고 검증용 User를 반환한다. */
  private User stubValidLocalLogin() {
    User user = UserFixture.user();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(Optional.of(user));
    given(authIdentityRepository.findByUserIdAndProvider(any(), eq(AuthProvider.LOCAL)))
        .willReturn(Optional.of(AuthIdentity.createLocal(null, "ENCODED")));
    given(passwordEncoder.matches("P@ssw0rd!", "ENCODED")).willReturn(true);
    given(refreshTokenStore.save(any(), anyString(), anyString())).willReturn(Duration.ofDays(14));
    return user;
  }

  private static SignupCommand command(String email) {
    return new SignupCommand(email, "P@ssw0rd!", "채소러버", "채소컴퍼니", Occupation.DEVELOPMENT,
        true, false);
  }

  @Test
  @DisplayName("미가입 이메일이면 인증 코드를 저장하고 메일을 보낸다")
  void sendSignupVerificationCode_success() {
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(Optional.empty());
    given(verificationCodeStore.tryAcquireSendSlot("user@chaeso.zip")).willReturn(true);

    String code = authService.sendSignupVerificationCode("  User@Chaeso.Zip  ");

    assertThat(code).isNull();
    verify(verificationCodeStore).saveCode(eq("user@chaeso.zip"), anyString());
    verify(verificationMailSender).sendVerificationCode(eq("user@chaeso.zip"), anyString());
  }

  @Test
  @DisplayName("메일 발송이 실패하면 쿨다운 슬롯을 해제해 즉시 재요청을 허용한다")
  void sendSignupVerificationCode_mailFailureReleasesCooldown() {
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(Optional.empty());
    given(verificationCodeStore.tryAcquireSendSlot("user@chaeso.zip")).willReturn(true);
    willThrow(new MailSendException("smtp down"))
        .given(verificationMailSender).sendVerificationCode(eq("user@chaeso.zip"), anyString());

    assertThatThrownBy(() -> authService.sendSignupVerificationCode("user@chaeso.zip"))
        .isInstanceOf(MailSendException.class);
    verify(verificationCodeStore).releaseSendSlot("user@chaeso.zip");
  }

  @Test
  @DisplayName("인증 코드 발송 쿨다운 중이면 AUTH-008로 실패하고 메일을 보내지 않는다")
  void sendSignupVerificationCode_cooldown() {
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(Optional.empty());
    given(verificationCodeStore.tryAcquireSendSlot("user@chaeso.zip")).willReturn(false);

    assertThatThrownBy(() -> authService.sendSignupVerificationCode("user@chaeso.zip"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.VERIFICATION_CODE_SEND_COOLDOWN);
    verify(verificationMailSender, never()).sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("이미 로컬로 가입된 이메일이면 EMAIL_ALREADY_EXISTS로 실패하고 메일을 보내지 않는다")
  void sendSignupVerificationCode_duplicate() {
    User user = UserFixture.user();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(Optional.of(user));
    given(authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL))
        .willReturn(Optional.of(AuthIdentity.createLocal(user.getId(), "ENCODED")));

    assertThatThrownBy(() -> authService.sendSignupVerificationCode("user@chaeso.zip"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    verify(verificationMailSender, never()).sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("구글로만 가입된 이메일이면 EMAIL_ALREADY_EXISTS를 던지지 않고 코드를 보낸 뒤 안내 코드를 돌려준다")
  void sendSignupVerificationCode_googleOnly_sendsCodeWithGuidance() {
    User user = UserFixture.user();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(Optional.of(user));
    given(authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL))
        .willReturn(Optional.empty());
    given(authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE))
        .willReturn(Optional.of(AuthIdentity.createGoogle(user.getId(), "google-sub-1")));
    given(verificationCodeStore.tryAcquireSendSlot("user@chaeso.zip")).willReturn(true);

    String code = authService.sendSignupVerificationCode("user@chaeso.zip");

    assertThat(code).isEqualTo("EMAIL_ALREADY_USED_WITH_GOOGLE");
    verify(verificationCodeStore).saveCode(eq("user@chaeso.zip"), anyString());
    verify(verificationMailSender).sendVerificationCode(eq("user@chaeso.zip"), anyString());
  }

  @Test
  @DisplayName("로컬과 구글이 모두 연결된 이메일이면 구글 안내가 아니라 EMAIL_ALREADY_EXISTS로 실패한다")
  void sendSignupVerificationCode_localAndGoogleLinked_stillRejected() {
    User user = UserFixture.user();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(Optional.of(user));
    given(authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL))
        .willReturn(Optional.of(AuthIdentity.createLocal(user.getId(), "ENCODED")));

    assertThatThrownBy(() -> authService.sendSignupVerificationCode("user@chaeso.zip"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    verify(authIdentityRepository, never()).findByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE);
  }

  @Test
  @DisplayName("인증 코드가 틀리면 VERIFICATION_CODE_INVALID로 실패한다")
  void verifySignupCode_invalid() {
    given(verificationCodeStore.verifyCode("user@chaeso.zip", "000000")).willReturn(false);

    assertThatThrownBy(() -> authService.verifySignupCode("user@chaeso.zip", "000000"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.VERIFICATION_CODE_INVALID);
  }

  @Test
  @DisplayName("이메일 인증이 완료되지 않으면 EMAIL_NOT_VERIFIED로 실패한다")
  void signup_notVerified() {
    given(verificationCodeStore.isVerified("user@chaeso.zip")).willReturn(false);
    SignupCommand command = command("user@chaeso.zip");

    assertThatThrownBy(() -> authService.signup(command))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.EMAIL_NOT_VERIFIED);
    verify(userRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("인증됐지만 이미 가입된 이메일이면 EMAIL_ALREADY_EXISTS로 실패한다")
  void signup_duplicate() {
    given(verificationCodeStore.isVerified("user@chaeso.zip")).willReturn(true);
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(true);
    SignupCommand command = command("user@chaeso.zip");

    assertThatThrownBy(() -> authService.signup(command))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
  }

  @Test
  @DisplayName("인증된 미가입 이메일이면 비밀번호를 인코딩해 회원과 로컬 인증 정보를 저장한다")
  void signup_success() {
    given(verificationCodeStore.isVerified("user@chaeso.zip")).willReturn(true);
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(false);
    given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
    given(passwordEncoder.encode("P@ssw0rd!")).willReturn("ENCODED");

    UserResponse response = authService.signup(command("user@chaeso.zip"));

    assertThat(response.email()).isEqualTo("user@chaeso.zip");
    assertThat(response.nickname()).isEqualTo("채소러버");

    ArgumentCaptor<AuthIdentity> captor = ArgumentCaptor.forClass(AuthIdentity.class);
    verify(authIdentityRepository).save(captor.capture());
    assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.LOCAL);
    assertThat(captor.getValue().getPasswordHash()).isEqualTo("ENCODED");
    verify(verificationCodeStore).clearVerified("user@chaeso.zip");
  }

  @Test
  @DisplayName("올바른 자격증명이면 액세스/리프레시 토큰과 만료 시간(초)을 반환한다")
  void login_success() {
    stubValidLocalLogin();
    given(jwtTokenProvider.createAccessToken(any())).willReturn("ACCESS");
    given(jwtTokenProvider.createRefreshToken(any(), anyString(), anyString())).willReturn("REFRESH");

    TokenResponse response = authService.login(loginCommand());

    assertThat(response.accessToken()).isEqualTo("ACCESS");
    assertThat(response.refreshToken()).isEqualTo("REFRESH");
    assertThat(response.accessTokenExpiresIn()).isEqualTo(Duration.ofMinutes(30).toSeconds());
    assertThat(response.refreshTokenExpiresIn()).isEqualTo(Duration.ofDays(14).toSeconds());
  }

  @Test
  @DisplayName("로그인 성공 시 마지막 로그인 시각/수단을 기록한다")
  void login_recordsLastLogin() {
    User user = stubValidLocalLogin();

    authService.login(loginCommand());

    assertThat(user.getLastLoginProvider()).isEqualTo(AuthProvider.LOCAL);
    assertThat(user.getLastLoginAt()).isNotNull();
  }

  @Test
  @DisplayName("리프레시 토큰 발급 시 UUID 형식 familyId/jti 를 생성해 전달한다")
  void login_generatesRefreshIdentifiers() {
    stubValidLocalLogin();

    authService.login(loginCommand());

    ArgumentCaptor<String> familyId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> jti = ArgumentCaptor.forClass(String.class);
    verify(jwtTokenProvider).createRefreshToken(any(), familyId.capture(), jti.capture());
    assertThat(UUID.fromString(familyId.getValue())).isNotNull();
    assertThat(UUID.fromString(jti.getValue())).isNotNull();
    assertThat(familyId.getValue()).isNotEqualTo(jti.getValue());
  }

  @Test
  @DisplayName("가입되지 않은 이메일이면 AUTH-003 으로 실패하고 토큰을 발급하지 않는다")
  void login_unknownEmail() {
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(Optional.empty());
    LoginCommand command = loginCommand();

    assertThatThrownBy(() -> authService.login(command))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    verify(jwtTokenProvider, never()).createAccessToken(any());
    verify(passwordEncoder).matches(anyString(), any());
  }

  @Test
  @DisplayName("LOCAL 인증정보가 없으면 AUTH-003 으로 실패한다")
  void login_missingLocalIdentity() {
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.of(UserFixture.user()));
    given(authIdentityRepository.findByUserIdAndProvider(any(), eq(AuthProvider.LOCAL)))
        .willReturn(Optional.empty());
    LoginCommand command = loginCommand();

    assertThatThrownBy(() -> authService.login(command))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("구글로만 가입된 계정으로 로컬 로그인을 시도하면 상수 시간 매칭 뒤 ACCOUNT_REGISTERED_WITH_GOOGLE로 실패한다")
  void login_googleOnlyAccount_rejectedWithGoogleGuidance() {
    User user = UserFixture.user();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(Optional.of(user));
    given(authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL))
        .willReturn(Optional.empty());
    given(authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE))
        .willReturn(Optional.of(AuthIdentity.createGoogle(user.getId(), "google-sub-1")));
    LoginCommand command = loginCommand();

    assertThatThrownBy(() -> authService.login(command))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.ACCOUNT_REGISTERED_WITH_GOOGLE);
    verify(passwordEncoder).matches(anyString(), any());
    verify(jwtTokenProvider, never()).createAccessToken(any());
  }

  @Test
  @DisplayName("비밀번호 해시가 없어도 상수 시간 유지를 위해 매칭을 1회 수행하고 AUTH-003 으로 실패한다")
  void login_nullPasswordHash() {
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.of(UserFixture.user()));
    given(authIdentityRepository.findByUserIdAndProvider(any(), eq(AuthProvider.LOCAL)))
        .willReturn(Optional.of(AuthIdentity.createLocal(null, null)));
    LoginCommand command = loginCommand();

    assertThatThrownBy(() -> authService.login(command))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    verify(passwordEncoder).matches(anyString(), any());
  }

  @Test
  @DisplayName("비밀번호가 일치하지 않으면 AUTH-003 으로 실패한다")
  void login_wrongPassword() {
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.of(UserFixture.user()));
    given(authIdentityRepository.findByUserIdAndProvider(any(), eq(AuthProvider.LOCAL)))
        .willReturn(Optional.of(AuthIdentity.createLocal(null, "ENCODED")));
    given(passwordEncoder.matches("P@ssw0rd!", "ENCODED")).willReturn(false);
    LoginCommand command = loginCommand();

    assertThatThrownBy(() -> authService.login(command))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    verify(jwtTokenProvider, never()).createAccessToken(any());
  }

  @Test
  @DisplayName("이메일을 정규화(trim+lowercase)한 뒤 조회한다")
  void login_normalizesEmail() {
    stubValidLocalLogin();
    given(jwtTokenProvider.createAccessToken(any())).willReturn("ACCESS");

    TokenResponse response = authService.login(new LoginCommand("  User@Chaeso.Zip  ", "P@ssw0rd!"));

    assertThat(response.accessToken()).isEqualTo("ACCESS");
  }

  @Test
  @DisplayName("로그인에 성공하면 발급한 refresh 토큰의 family를 저장소에 기록한다")
  void login_savesRefreshTokenFamily() {
    User user = UserFixture.user("login@chaeso.zip");
    given(userRepository.findByEmailAndDeletedAtIsNull("login@chaeso.zip"))
        .willReturn(Optional.of(user));
    given(authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL))
        .willReturn(Optional.of(AuthIdentity.createLocal(user.getId(), "hashed")));
    given(passwordEncoder.matches("P@ssw0rd!", "hashed")).willReturn(true);
    given(jwtTokenProvider.createAccessToken(user.getId())).willReturn("access-token");
    given(jwtTokenProvider.createRefreshToken(eq(user.getId()), anyString(), anyString()))
        .willReturn("refresh-token");
    given(refreshTokenStore.save(any(), anyString(), anyString())).willReturn(Duration.ofDays(14));

    authService.login(new LoginCommand("login@chaeso.zip", "P@ssw0rd!"));

    ArgumentCaptor<String> familyId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> jti = ArgumentCaptor.forClass(String.class);
    verify(refreshTokenStore).save(eq(user.getId()), familyId.capture(), jti.capture());
    verify(jwtTokenProvider)
        .createRefreshToken(user.getId(), familyId.getValue(), jti.getValue());
  }

  @Test
  @DisplayName("유효한 refresh 토큰이면 familyId를 유지한 채 새 jti로 회전해 토큰 쌍을 재발급한다")
  void reissue_validToken_rotatesWithinSameFamily() {
    UUID userId = UUID.randomUUID();
    given(jwtTokenProvider.parseRefresh("valid-refresh"))
        .willReturn(new RefreshTokenInfo(userId, "family-1", "jti-1"));
    given(refreshTokenStore.rotate(eq(userId), eq("family-1"), eq("jti-1"), anyString()))
        .willReturn(new RotateOutcome(RotateResult.ROTATED, Duration.ofDays(14)));
    given(jwtTokenProvider.createAccessToken(userId)).willReturn("new-access");
    given(jwtTokenProvider.createRefreshToken(eq(userId), eq("family-1"), anyString()))
        .willReturn("new-refresh");

    TokenResponse response = authService.reissue("valid-refresh");

    assertThat(response.accessToken()).isEqualTo("new-access");
    assertThat(response.refreshToken()).isEqualTo("new-refresh");
    assertThat(response.accessTokenExpiresIn()).isEqualTo(1800L);
    assertThat(response.refreshTokenExpiresIn()).isEqualTo(1209600L);

    ArgumentCaptor<String> rotatedJti = ArgumentCaptor.forClass(String.class);
    verify(refreshTokenStore).rotate(eq(userId), eq("family-1"), eq("jti-1"), rotatedJti.capture());
    verify(jwtTokenProvider).createRefreshToken(userId, "family-1", rotatedJti.getValue());
  }

  @Test
  @DisplayName("절대만료가 가까워 세션 TTL이 잘리면 응답의 만료(초)도 잘린 값으로 내려간다")
  void reissue_nearAbsoluteDeadline_reportsTruncatedExpiry() {
    UUID userId = UUID.randomUUID();
    given(jwtTokenProvider.parseRefresh("valid-refresh"))
        .willReturn(new RefreshTokenInfo(userId, "family-1", "jti-1"));
    given(refreshTokenStore.rotate(eq(userId), eq("family-1"), eq("jti-1"), anyString()))
        .willReturn(new RotateOutcome(RotateResult.ROTATED, Duration.ofDays(5)));

    TokenResponse response = authService.reissue("valid-refresh");

    assertThat(response.refreshTokenExpiresIn()).isEqualTo(Duration.ofDays(5).toSeconds());
  }

  @Test
  @DisplayName("서명이 깨졌거나 만료된 refresh 토큰이면 AUTH-004를 던진다")
  void reissue_malformedToken_throwsInvalidRefreshToken() {
    given(jwtTokenProvider.parseRefresh("tampered"))
        .willThrow(new InvalidTokenException("유효하지 않은 토큰입니다."));

    assertThatThrownBy(() -> authService.reissue("tampered"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);

    verify(refreshTokenStore, never()).rotate(any(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("저장소에 없는 세션이면 AUTH-004를 던진다")
  void reissue_unknownSession_throwsInvalidRefreshToken() {
    UUID userId = UUID.randomUUID();
    given(jwtTokenProvider.parseRefresh("orphan"))
        .willReturn(new RefreshTokenInfo(userId, "family-1", "jti-1"));
    given(refreshTokenStore.rotate(eq(userId), eq("family-1"), eq("jti-1"), anyString()))
        .willReturn(new RotateOutcome(RotateResult.INVALID, null));

    assertThatThrownBy(() -> authService.reissue("orphan"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
  }

  @Test
  @DisplayName("이미 회전된 토큰을 재사용하면 AUTH-005를 던진다")
  void reissue_reusedToken_throwsReuseDetected() {
    UUID userId = UUID.randomUUID();
    given(jwtTokenProvider.parseRefresh("replayed"))
        .willReturn(new RefreshTokenInfo(userId, "family-1", "old-jti"));
    given(refreshTokenStore.rotate(eq(userId), eq("family-1"), eq("old-jti"), anyString()))
        .willReturn(new RotateOutcome(RotateResult.REUSED, null));

    assertThatThrownBy(() -> authService.reissue("replayed"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);

    verify(refreshTokenStore, never()).revoke(any(), anyString());
  }

  @Test
  @DisplayName("로그아웃하면 해당 refresh 토큰의 family 세션을 폐기한다")
  void logout_revokesFamily() {
    UUID userId = UUID.randomUUID();
    given(jwtTokenProvider.parseRefresh("my-refresh"))
        .willReturn(new RefreshTokenInfo(userId, "family-1", "jti-1"));

    authService.logout(userId, "my-refresh");

    verify(refreshTokenStore).revoke(userId, "family-1");
  }

  @Test
  @DisplayName("다른 사용자의 refresh 토큰으로 로그아웃하면 AUTH-004를 던지고 아무것도 폐기하지 않는다")
  void logout_otherUsersToken_throwsAndRevokesNothing() {
    UUID attacker = UUID.randomUUID();
    UUID victim = UUID.randomUUID();
    given(jwtTokenProvider.parseRefresh("victim-refresh"))
        .willReturn(new RefreshTokenInfo(victim, "victim-family", "jti-1"));

    assertThatThrownBy(() -> authService.logout(attacker, "victim-refresh"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);

    verify(refreshTokenStore, never()).revoke(any(), anyString());
  }

  private static final GoogleIdTokenInfo GOOGLE_INFO =
      new GoogleIdTokenInfo("google-sub-1", "user@chaeso.zip", "홍길동");

  /** 구글이 검증한 idToken 이 들어오는 상황. 이후 분기는 계정 상태가 정한다. */
  private void givenVerifiedGoogleToken() {
    given(googleIdTokenVerifier.verify("id-token")).willReturn(GOOGLE_INFO);
  }

  private void givenGoogleIdentity(User user, boolean linked) {
    given(authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE))
        .willReturn(linked
            ? Optional.of(AuthIdentity.createGoogle(user.getId(), "google-sub-1"))
            : Optional.empty());
  }

  @Test
  @DisplayName("구글 계정이 연결된 유저면 토큰을 발급하고 GOOGLE 로그인으로 기록한다")
  void googleAuth_linkedUser_login() {
    givenVerifiedGoogleToken();
    User user = UserFixture.user();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.of(user));
    givenGoogleIdentity(user, true);
    given(refreshTokenStore.save(any(), anyString(), anyString())).willReturn(Duration.ofDays(14));
    given(jwtTokenProvider.createAccessToken(any())).willReturn("access");
    given(jwtTokenProvider.createRefreshToken(any(), anyString(), anyString())).willReturn("refresh");

    GoogleAuthResponse response = authService.googleAuth("id-token");

    assertThat(response.accessToken()).isEqualTo("access");
    assertThat(response.refreshToken()).isEqualTo("refresh");
    assertThat(response.linkRequired()).isNull();
    assertThat(response.signupRequired()).isNull();
    assertThat(user.getLastLoginProvider()).isEqualTo(AuthProvider.GOOGLE);
  }

  @Test
  @DisplayName("같은 이메일의 로컬 계정만 있으면 연결하지 않고 linkRequired 로 사용자 확인을 요구한다")
  void googleAuth_localOnly_requiresLinkConfirmation() {
    givenVerifiedGoogleToken();
    User user = UserFixture.user();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.of(user));
    givenGoogleIdentity(user, false);

    GoogleAuthResponse response = authService.googleAuth("id-token");

    assertThat(response.linkRequired()).isTrue();
    assertThat(response.email()).isEqualTo("user@chaeso.zip");
    assertThat(response.accessToken()).isNull();
    assertThat(response.refreshToken()).isNull();
  }

  @Test
  @DisplayName("자동 연결 금지: linkRequired 시점에는 AuthIdentity 도 토큰도 만들지 않는다")
  void googleAuth_localOnly_createsNothing() {
    givenVerifiedGoogleToken();
    User user = UserFixture.user();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.of(user));
    givenGoogleIdentity(user, false);

    authService.googleAuth("id-token");

    verify(authIdentityRepository, never()).save(any());
    verify(refreshTokenStore, never()).save(any(), anyString(), anyString());
  }

  @Test
  @DisplayName("가입 이력이 없으면 구글 클레임을 보관하고 signupToken 과 프리필을 발급한다")
  void googleAuth_newUser_issuesSignupToken() {
    givenVerifiedGoogleToken();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.empty());
    given(googleSignupStore.save(GOOGLE_INFO)).willReturn("signup-ticket");

    GoogleAuthResponse response = authService.googleAuth("id-token");

    assertThat(response.signupRequired()).isTrue();
    assertThat(response.signupToken()).isEqualTo("signup-ticket");
    assertThat(response.prefill().email()).isEqualTo("user@chaeso.zip");
    assertThat(response.prefill().suggestedNickname()).isEqualTo("홍길동");
    assertThat(response.accessToken()).isNull();
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("구글 계정에 이름이 없으면 프리필 닉네임은 null 이다")
  void googleAuth_newUserWithoutName_prefillNicknameIsNull() {
    GoogleIdTokenInfo noName = new GoogleIdTokenInfo("google-sub-1", "user@chaeso.zip", null);
    given(googleIdTokenVerifier.verify("id-token")).willReturn(noName);
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.empty());
    given(googleSignupStore.save(noName)).willReturn("signup-ticket");

    GoogleAuthResponse response = authService.googleAuth("id-token");

    assertThat(response.prefill().suggestedNickname()).isNull();
  }

  @Test
  @DisplayName("구글 이메일은 정규화해 같은 계정으로 매핑한다")
  void googleAuth_normalizesEmail() {
    given(googleIdTokenVerifier.verify("id-token"))
        .willReturn(new GoogleIdTokenInfo("google-sub-1", "  User@Chaeso.ZIP ", "홍길동"));
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.empty());
    given(googleSignupStore.save(any())).willReturn("signup-ticket");

    GoogleAuthResponse response = authService.googleAuth("id-token");

    assertThat(response.prefill().email()).isEqualTo("user@chaeso.zip");
  }

  @Test
  @DisplayName("사용자 확인 후 연결하면 GOOGLE identity 를 만들고 토큰을 발급한다")
  void linkGoogle_createsIdentityAndIssuesTokens() {
    givenVerifiedGoogleToken();
    User user = UserFixture.user();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.of(user));
    givenGoogleIdentity(user, false);
    given(authIdentityRepository.findByProviderAndProviderUid(AuthProvider.GOOGLE, "google-sub-1"))
        .willReturn(Optional.empty());
    given(refreshTokenStore.save(any(), anyString(), anyString())).willReturn(Duration.ofDays(14));
    given(jwtTokenProvider.createAccessToken(any())).willReturn("access");
    given(jwtTokenProvider.createRefreshToken(any(), anyString(), anyString())).willReturn("refresh");

    TokenResponse response = authService.linkGoogle("id-token");

    assertThat(response.accessToken()).isEqualTo("access");
    ArgumentCaptor<AuthIdentity> captor = ArgumentCaptor.forClass(AuthIdentity.class);
    verify(authIdentityRepository).save(captor.capture());
    assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(captor.getValue().getProviderUid()).isEqualTo("google-sub-1");
    assertThat(captor.getValue().getPasswordHash()).isNull();
  }

  @Test
  @DisplayName("동시 제출(더블클릭) 레이스로 유니크 제약과 충돌해도 토큰을 발급한다")
  void linkGoogle_uniqueViolation_stillIssuesTokens() {
    givenVerifiedGoogleToken();
    User user = UserFixture.user();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.of(user));
    givenGoogleIdentity(user, false);
    given(authIdentityRepository.findByProviderAndProviderUid(AuthProvider.GOOGLE, "google-sub-1"))
        .willReturn(Optional.empty());
    willThrow(new DataIntegrityViolationException("duplicate"))
        .given(authIdentityRepository).save(any());
    given(refreshTokenStore.save(any(), anyString(), anyString())).willReturn(Duration.ofDays(14));
    given(jwtTokenProvider.createAccessToken(any())).willReturn("access");
    given(jwtTokenProvider.createRefreshToken(any(), anyString(), anyString())).willReturn("refresh");

    TokenResponse response = authService.linkGoogle("id-token");

    assertThat(response.accessToken()).isEqualTo("access");
  }

  @Test
  @DisplayName("소프트 삭제된 계정에 남은 identity 는 새로 만들지 않고 새 유저로 재소유시킨다")
  void linkGoogle_orphanedIdentity_reassignsToNewOwner() {
    givenVerifiedGoogleToken();
    User user = UserFixture.user();
    UUID deletedOwnerId = UUID.randomUUID();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.of(user));
    givenGoogleIdentity(user, false);
    AuthIdentity orphaned = AuthIdentity.createGoogle(deletedOwnerId, "google-sub-1");
    given(authIdentityRepository.findByProviderAndProviderUid(AuthProvider.GOOGLE, "google-sub-1"))
        .willReturn(Optional.of(orphaned));
    given(userRepository.findByIdAndDeletedAtIsNull(deletedOwnerId)).willReturn(Optional.empty());
    given(refreshTokenStore.save(any(), anyString(), anyString())).willReturn(Duration.ofDays(14));
    given(jwtTokenProvider.createAccessToken(any())).willReturn("access");
    given(jwtTokenProvider.createRefreshToken(any(), anyString(), anyString())).willReturn("refresh");

    TokenResponse response = authService.linkGoogle("id-token");

    assertThat(response.accessToken()).isEqualTo("access");
    ArgumentCaptor<AuthIdentity> captor = ArgumentCaptor.forClass(AuthIdentity.class);
    verify(authIdentityRepository).save(captor.capture());
    assertThat(captor.getValue()).isSameAs(orphaned);
    assertThat(captor.getValue().getUserId()).isEqualTo(user.getId());
  }

  @Test
  @DisplayName("같은 구글 계정이 이미 활성 유저에게 연결돼 있으면 재소유하지 않고 AUTH-009로 거부한다")
  void linkGoogle_identityOwnedByActiveUser_rejected() {
    givenVerifiedGoogleToken();
    User user = UserFixture.user();
    UUID otherUserId = UUID.randomUUID();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.of(user));
    givenGoogleIdentity(user, false);
    AuthIdentity ownedByOther = AuthIdentity.createGoogle(otherUserId, "google-sub-1");
    given(authIdentityRepository.findByProviderAndProviderUid(AuthProvider.GOOGLE, "google-sub-1"))
        .willReturn(Optional.of(ownedByOther));
    given(userRepository.findByIdAndDeletedAtIsNull(otherUserId))
        .willReturn(Optional.of(UserFixture.user("other@chaeso.zip")));

    assertThatThrownBy(() -> authService.linkGoogle("id-token"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.GOOGLE_AUTH_FAILED);
    verify(authIdentityRepository, never()).save(any());
    verify(refreshTokenStore, never()).save(any(), anyString(), anyString());
  }

  @Test
  @DisplayName("이미 연결된 계정에 다시 연결해도 identity 를 새로 만들지 않고 토큰만 발급한다")
  void linkGoogle_alreadyLinked_isIdempotent() {
    givenVerifiedGoogleToken();
    User user = UserFixture.user();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.of(user));
    givenGoogleIdentity(user, true);
    given(refreshTokenStore.save(any(), anyString(), anyString())).willReturn(Duration.ofDays(14));
    given(jwtTokenProvider.createAccessToken(any())).willReturn("access");
    given(jwtTokenProvider.createRefreshToken(any(), anyString(), anyString())).willReturn("refresh");

    TokenResponse response = authService.linkGoogle("id-token");

    assertThat(response.accessToken()).isEqualTo("access");
    verify(authIdentityRepository, never()).save(any());
  }

  @Test
  @DisplayName("연결 확인 중 idToken 이 만료되면 AUTH-009로 떨어지고 아무것도 연결하지 않는다")
  void linkGoogle_expiredIdToken_rejected() {
    willThrow(new AuthBusinessException(AuthErrorCode.GOOGLE_AUTH_FAILED))
        .given(googleIdTokenVerifier).verify("expired-token");

    assertThatThrownBy(() -> authService.linkGoogle("expired-token"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.GOOGLE_AUTH_FAILED);

    verify(authIdentityRepository, never()).save(any());
    verify(refreshTokenStore, never()).save(any(), anyString(), anyString());
  }

  @Test
  @DisplayName("붙을 계정이 사라졌으면 AUTH-009로 떨어져 구글 로그인을 다시 태우게 한다")
  void linkGoogle_noAccount_rejected() {
    givenVerifiedGoogleToken();
    given(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip"))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.linkGoogle("id-token"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.GOOGLE_AUTH_FAILED);

    verify(authIdentityRepository, never()).save(any());
  }

  private static GoogleSignupCommand googleSignupCommand(String signupToken) {
    return new GoogleSignupCommand(signupToken, "채소러버", "채소컴퍼니", Occupation.DEVELOPMENT, true,
        false);
  }

  @Test
  @DisplayName("가입 티켓이 없거나 만료됐으면 GOOGLE_SIGNUP_SESSION_INVALID로 실패한다")
  void signupGoogle_missingTicket_rejected() {
    given(googleSignupStore.find("no-such-token")).willReturn(Optional.empty());
    GoogleSignupCommand command = googleSignupCommand("no-such-token");

    assertThatThrownBy(() -> authService.signupGoogle(command))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.GOOGLE_SIGNUP_SESSION_INVALID);

    verify(userRepository, never()).saveAndFlush(any());
    verify(authIdentityRepository, never()).save(any());
  }

  @Test
  @DisplayName("유효한 티켓이면 회원과 GOOGLE 인증정보를 만들고 티켓을 폐기한 뒤 토큰을 발급한다")
  void signupGoogle_success() {
    given(googleSignupStore.find("signup-ticket")).willReturn(Optional.of(GOOGLE_INFO));
    given(userRepository.saveAndFlush(any(User.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(refreshTokenStore.save(any(), anyString(), anyString())).willReturn(Duration.ofDays(14));
    given(jwtTokenProvider.createAccessToken(any())).willReturn("access");
    given(jwtTokenProvider.createRefreshToken(any(), anyString(), anyString())).willReturn("refresh");

    TokenResponse response = authService.signupGoogle(googleSignupCommand("signup-ticket"));

    assertThat(response.accessToken()).isEqualTo("access");
    assertThat(response.refreshToken()).isEqualTo("refresh");

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(userCaptor.capture());
    assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@chaeso.zip");
    assertThat(userCaptor.getValue().getNickname()).isEqualTo("채소러버");
    assertThat(userCaptor.getValue().getLastLoginProvider()).isEqualTo(AuthProvider.GOOGLE);

    ArgumentCaptor<AuthIdentity> identityCaptor = ArgumentCaptor.forClass(AuthIdentity.class);
    verify(authIdentityRepository).save(identityCaptor.capture());
    assertThat(identityCaptor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(identityCaptor.getValue().getProviderUid()).isEqualTo("google-sub-1");
    assertThat(identityCaptor.getValue().getPasswordHash()).isNull();

    verify(googleSignupStore).delete("signup-ticket");
  }

  @Test
  @DisplayName("가입 처리 중 타인이 먼저 같은 이메일로 가입했으면 EMAIL_ALREADY_EXISTS로 실패하고 티켓을 남겨둔다")
  void signupGoogle_emailTakenConcurrently_rejected() {
    given(googleSignupStore.find("signup-ticket")).willReturn(Optional.of(GOOGLE_INFO));
    willThrow(new DataIntegrityViolationException("duplicate"))
        .given(userRepository).saveAndFlush(any(User.class));
    GoogleSignupCommand command = googleSignupCommand("signup-ticket");

    assertThatThrownBy(() -> authService.signupGoogle(command))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);

    verify(authIdentityRepository, never()).save(any());
    verify(googleSignupStore, never()).delete(anyString());
  }

  @Test
  @DisplayName("가입 대상 이메일의 구글 sub 가 이미 활성 유저에게 연결돼 있으면 방금 만든 유저를 되돌리고 티켓을 남겨둔다")
  void signupGoogle_identityOwnedByActiveUser_rollsBackCreatedUser() {
    given(googleSignupStore.find("signup-ticket")).willReturn(Optional.of(GOOGLE_INFO));
    given(userRepository.saveAndFlush(any(User.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    UUID otherUserId = UUID.randomUUID();
    AuthIdentity ownedByOther = AuthIdentity.createGoogle(otherUserId, "google-sub-1");
    given(authIdentityRepository.findByProviderAndProviderUid(AuthProvider.GOOGLE, "google-sub-1"))
        .willReturn(Optional.of(ownedByOther));
    given(userRepository.findByIdAndDeletedAtIsNull(otherUserId))
        .willReturn(Optional.of(UserFixture.user("other@chaeso.zip")));
    GoogleSignupCommand command = googleSignupCommand("signup-ticket");

    assertThatThrownBy(() -> authService.signupGoogle(command))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.GOOGLE_AUTH_FAILED);

    ArgumentCaptor<User> deletedUserCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).delete(deletedUserCaptor.capture());
    assertThat(deletedUserCaptor.getValue().getEmail()).isEqualTo("user@chaeso.zip");
    verify(authIdentityRepository, never()).save(any());
    verify(googleSignupStore, never()).delete(anyString());
    verify(refreshTokenStore, never()).save(any(), anyString(), anyString());
  }
}
