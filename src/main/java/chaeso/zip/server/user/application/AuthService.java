package chaeso.zip.server.user.application;

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
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserErrorCode;
import chaeso.zip.server.user.domain.UserRepository;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuthService {

  private static final int CODE_LENGTH = 6;
  private static final int CODE_UPPER_BOUND = 1_000_000;
  private static final String ACTIVE_EMAIL_UNIQUE_CONSTRAINT = "uq_users_email_active";

  private final UserRepository userRepository;
  private final EmailVerificationCodeStore verificationCodeStore;
  private final VerificationMailSender verificationMailSender;
  private final AuthIdentityRepository authIdentityRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenStore refreshTokenStore;

  private final SecureRandom secureRandom = new SecureRandom();

  public void sendSignupVerificationCode(String email) {
    String normalized = normalizeEmail(email);
    if (userRepository.existsByEmailAndDeletedAtIsNull(normalized)) {
      throw new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }
    String code = generateCode();
    verificationCodeStore.saveCode(normalized, code);
    verificationMailSender.sendVerificationCode(normalized, code);
  }

  public void verifySignupCode(String email, String code) {
    String normalized = normalizeEmail(email);
    String storedCode = verificationCodeStore.findCode(normalized)
        .orElseThrow(() -> new BusinessException(UserErrorCode.VERIFICATION_CODE_INVALID));
    if (!storedCode.equals(code)) {
      throw new BusinessException(UserErrorCode.VERIFICATION_CODE_INVALID);
    }
    verificationCodeStore.markVerified(normalized);
  }

  @Transactional
  public UserResponse signup(SignupCommand command) {
    String email = normalizeEmail(command.email());
    if (!verificationCodeStore.isVerified(email)) {
      throw new BusinessException(UserErrorCode.EMAIL_NOT_VERIFIED);
    }
    if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
      throw new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }

    User user = saveUser(command, email);
    authIdentityRepository.save(
        AuthIdentity.createLocal(user.getId(), passwordEncoder.encode(command.rawPassword())));
    clearVerificationAfterCommit(email);
    return UserResponse.from(user);
  }

  @Transactional
  public TokenResponse login(LoginCommand command) {
    User user = userRepository.findByEmailAndDeletedAtIsNull(normalizeEmail(command.email()))
        .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_CREDENTIALS));
    AuthIdentity identity = authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
        .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_CREDENTIALS));
    if (identity.getPasswordHash() == null
        || !passwordEncoder.matches(command.rawPassword(), identity.getPasswordHash())) {
      throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
    }

    user.recordLogin(AuthProvider.LOCAL);
    return issueTokens(user);
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private User saveUser(SignupCommand command, String email) {
    try {
      return userRepository.saveAndFlush(User.create(
          email,
          command.nickname(),
          command.employmentStatus(),
          command.companyName(),
          command.occupation(),
          command.termsAgreed(),
          command.termsVersion(),
          command.marketingAgreed()));
    } catch (DataIntegrityViolationException exception) {
      if (hasConstraint(exception, ACTIVE_EMAIL_UNIQUE_CONSTRAINT)) {
        throw new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
      }
      throw exception;
    }
  }

  private boolean hasConstraint(Throwable exception, String constraintName) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof ConstraintViolationException constraintViolation
          && constraintName.equals(constraintViolation.getConstraintName())) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private void clearVerificationAfterCommit(String email) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      verificationCodeStore.clearVerified(email);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        try {
          verificationCodeStore.clearVerified(email);
        } catch (RuntimeException exception) {
          log.warn("가입 완료 후 이메일 인증 상태 정리에 실패했습니다. TTL 만료를 기다립니다.", exception);
        }
      }
    });
  }

  private TokenResponse issueTokens(User user) {
    String familyId = UUID.randomUUID().toString();
    String jti = UUID.randomUUID().toString();
    refreshTokenStore.save(user.getId(), familyId, jti);
    return new TokenResponse(
        jwtTokenProvider.createAccessToken(user.getId()),
        jwtTokenProvider.createRefreshToken(user.getId(), familyId, jti));
  }

  private String generateCode() {
    return String.format("%0" + CODE_LENGTH + "d", secureRandom.nextInt(CODE_UPPER_BOUND));
  }
}
