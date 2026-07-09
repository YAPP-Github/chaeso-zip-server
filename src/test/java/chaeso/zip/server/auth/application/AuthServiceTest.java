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

import chaeso.zip.server.auth.application.dto.SignupCommand;
import chaeso.zip.server.auth.application.dto.UserResponse;
import chaeso.zip.server.auth.domain.AuthBusinessException;
import chaeso.zip.server.auth.domain.AuthErrorCode;
import chaeso.zip.server.auth.domain.AuthIdentity;
import chaeso.zip.server.auth.domain.AuthIdentityRepository;
import chaeso.zip.server.auth.domain.AuthProvider;
import chaeso.zip.server.auth.infrastructure.mail.VerificationMailSender;
import chaeso.zip.server.auth.infrastructure.verification.EmailVerificationCodeStore;
import chaeso.zip.server.user.application.ConsentProperties;
import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

  private AuthServiceImpl authService;

  @BeforeEach
  void setUp() {
    authService = new AuthServiceImpl(
        userRepository,
        authIdentityRepository,
        verificationCodeStore,
        verificationMailSender,
        passwordEncoder,
        new ConsentProperties("v1.0"));
  }

  private static SignupCommand command(String email) {
    return new SignupCommand(email, "P@ssw0rd!", "채소러버", "채소컴퍼니", Occupation.DEVELOPMENT,
        true, false);
  }

  @Test
  @DisplayName("미가입 이메일이면 인증 코드를 저장하고 메일을 보낸다")
  void sendSignupVerificationCode_success() {
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(false);
    given(verificationCodeStore.tryAcquireSendSlot("user@chaeso.zip")).willReturn(true);

    authService.sendSignupVerificationCode("  User@Chaeso.Zip  ");

    verify(verificationCodeStore).saveCode(eq("user@chaeso.zip"), anyString());
    verify(verificationMailSender).sendVerificationCode(eq("user@chaeso.zip"), anyString());
  }

  @Test
  @DisplayName("메일 발송이 실패하면 쿨다운 슬롯을 해제해 즉시 재요청을 허용한다")
  void sendSignupVerificationCode_mailFailureReleasesCooldown() {
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(false);
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
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(false);
    given(verificationCodeStore.tryAcquireSendSlot("user@chaeso.zip")).willReturn(false);

    assertThatThrownBy(() -> authService.sendSignupVerificationCode("user@chaeso.zip"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.VERIFICATION_CODE_SEND_COOLDOWN);
    verify(verificationMailSender, never()).sendVerificationCode(anyString(), anyString());
  }

  @Test
  @DisplayName("이미 가입된 이메일이면 EMAIL_ALREADY_EXISTS로 실패하고 메일을 보내지 않는다")
  void sendSignupVerificationCode_duplicate() {
    given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(true);

    assertThatThrownBy(() -> authService.sendSignupVerificationCode("user@chaeso.zip"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    verify(verificationMailSender, never()).sendVerificationCode(anyString(), anyString());
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
}
