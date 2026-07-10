package chaeso.zip.server.auth.application;

import chaeso.zip.server.auth.application.dto.LoginCommand;
import chaeso.zip.server.auth.application.dto.SignupCommand;
import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.application.dto.UserResponse;
import chaeso.zip.server.auth.domain.AuthBusinessException;
import chaeso.zip.server.auth.domain.AuthErrorCode;
import chaeso.zip.server.auth.domain.AuthIdentity;
import chaeso.zip.server.auth.domain.AuthIdentityRepository;
import chaeso.zip.server.auth.domain.AuthProvider;
import chaeso.zip.server.auth.infrastructure.jwt.JwtProperties;
import chaeso.zip.server.auth.infrastructure.jwt.JwtTokenProvider;
import chaeso.zip.server.auth.infrastructure.mail.VerificationMailSender;
import chaeso.zip.server.auth.infrastructure.verification.EmailVerificationCodeStore;
import chaeso.zip.server.user.application.ConsentProperties;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserRepository;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AuthService} 구현. 이메일은 trim + lowercase 로 정규화해 동일 계정으로 취급한다.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private static final int CODE_UPPER_BOUND = 1_000_000;

  private final UserRepository userRepository;
  private final AuthIdentityRepository authIdentityRepository;
  private final EmailVerificationCodeStore verificationCodeStore;
  private final VerificationMailSender verificationMailSender;
  private final PasswordEncoder passwordEncoder;
  private final ConsentProperties consentProperties;
  private final JwtTokenProvider jwtTokenProvider;
  private final JwtProperties jwtProperties;

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public void sendSignupVerificationCode(String email) {
    String normalized = normalizeEmail(email);
    // TODO(google): 구글 로그인 도입 시 user 단위 존재 검사를 AuthIdentity provider 단위로 승격 —
    //   로컬 가입 존재시 = 409 EMAIL_ALREADY_EXISTS
    //   구글 가입 존재시 = 200 EMAIL_ALREADY_USED_WITH_GOOGLE(연결) / 신규=200 발송
    if (userRepository.existsByEmailAndDeletedAtIsNull(normalized)) {
      throw new AuthBusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }
    if (!verificationCodeStore.tryAcquireSendSlot(normalized)) {
      throw new AuthBusinessException(AuthErrorCode.VERIFICATION_CODE_SEND_COOLDOWN);
    }
    String code = generateCode();
    verificationCodeStore.saveCode(normalized, code);
    try {
      verificationMailSender.sendVerificationCode(normalized, code);
    } catch (RuntimeException exception) {
      verificationCodeStore.releaseSendSlot(normalized);
      throw exception;
    }
  }

  @Override
  public void verifySignupCode(String email, String code) {
    if (!verificationCodeStore.verifyCode(normalizeEmail(email), code)) {
      throw new AuthBusinessException(AuthErrorCode.VERIFICATION_CODE_INVALID);
    }
  }

  @Override
  @Transactional
  public UserResponse signup(SignupCommand command) {
    String email = normalizeEmail(command.email());
    if (!verificationCodeStore.isVerified(email)) {
      throw new AuthBusinessException(AuthErrorCode.EMAIL_NOT_VERIFIED);
    }
    if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
      throw new AuthBusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }

    User user = saveUser(command, email);
    authIdentityRepository.save(
        AuthIdentity.createLocal(user.getId(), passwordEncoder.encode(command.rawPassword())));
    verificationCodeStore.clearVerified(email);
    return UserResponse.from(user);
  }

  @Override
  @Transactional
  public TokenResponse login(LoginCommand command) {
    String email = normalizeEmail(command.email());
    User user = userRepository.findByEmailAndDeletedAtIsNull(email)
        .orElseThrow(() -> new AuthBusinessException(AuthErrorCode.INVALID_CREDENTIALS));
    AuthIdentity identity = authIdentityRepository
        .findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
        .orElseThrow(() -> new AuthBusinessException(AuthErrorCode.INVALID_CREDENTIALS));

    String passwordHash = identity.getPasswordHash();
    if (passwordHash == null || passwordHash.isBlank()
        || !passwordEncoder.matches(command.rawPassword(), passwordHash)) {
      throw new AuthBusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    user.recordLogin(AuthProvider.LOCAL);

    UUID userId = user.getId();
    String familyId = UUID.randomUUID().toString();
    String jti = UUID.randomUUID().toString();
    return new TokenResponse(
        jwtTokenProvider.createAccessToken(userId),
        jwtTokenProvider.createRefreshToken(userId, familyId, jti),
        jwtProperties.accessTtl().toSeconds(),
        jwtProperties.refreshTtl().toSeconds());
  }

  private User saveUser(SignupCommand command, String email) {
    try {
      return userRepository.saveAndFlush(User.create(
          email,
          command.nickname(),
          command.companyName(),
          command.occupation(),
          command.termsAgreed(),
          command.marketingAgreed(),
          consentProperties.toVersions()));
    } catch (DataIntegrityViolationException exception) {
      throw new AuthBusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }
  }

  private String generateCode() {
    return String.format("%06d", secureRandom.nextInt(CODE_UPPER_BOUND));
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
