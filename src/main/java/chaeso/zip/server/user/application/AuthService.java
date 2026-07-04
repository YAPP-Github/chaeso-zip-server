package chaeso.zip.server.user.application;

import chaeso.zip.server.common.exception.BusinessException;
import chaeso.zip.server.common.mail.VerificationMailSender;
import chaeso.zip.server.common.security.EmailVerificationCodeStore;
import chaeso.zip.server.common.security.InvalidTokenException;
import chaeso.zip.server.common.security.JwtTokenProvider;
import chaeso.zip.server.common.security.RefreshTokenInfo;
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
    if (!verificationCodeStore.verifyCode(normalized, code)) {
      throw new BusinessException(UserErrorCode.VERIFICATION_CODE_INVALID);
    }
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
    clearVerification(email);
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

  private void clearVerification(String email) {
    try {
      verificationCodeStore.clearVerified(email);
    } catch (RuntimeException exception) {
      log.warn("Failed to clear email verification status after signup. Waiting for TTL expiration.", exception);
    }
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

  public TokenResponse reissue(String refreshToken) {
    RefreshTokenInfo info;
    try {
      info = jwtTokenProvider.parseRefresh(refreshToken);
    } catch (InvalidTokenException e) {
      throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }

    String storedJti = refreshTokenStore.findJti(info.userId(), info.familyId())
        .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN));
    if (!storedJti.equals(info.jti())) {
      revokeSessionsForReuse(info);
    }

    userRepository.findByIdAndDeletedAtIsNull(info.userId())
        .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN));
    String newJti = UUID.randomUUID().toString();
    TokenResponse tokens = new TokenResponse(
        jwtTokenProvider.createAccessToken(info.userId()),
        jwtTokenProvider.createRefreshToken(info.userId(), info.familyId(), newJti));

    RefreshTokenStore.RotateResult rotation =
        refreshTokenStore.rotate(info.userId(), info.familyId(), info.jti(), newJti);
    if (rotation == RefreshTokenStore.RotateResult.MISSING) {
      throw new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    if (rotation == RefreshTokenStore.RotateResult.REUSE) {
      revokeSessionsForReuse(info);
    }

    return tokens;
  }

  private void revokeSessionsForReuse(RefreshTokenInfo info) {
    refreshTokenStore.deleteAllForUser(info.userId());
    log.warn("Refresh token reuse detected. userId={}, familyId={}", info.userId(), info.familyId());
    throw new BusinessException(UserErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
  }

  public void logout(String refreshToken) {
    try {
      RefreshTokenInfo info = jwtTokenProvider.parseRefresh(refreshToken);
      refreshTokenStore.deleteFamily(info.userId(), info.familyId());
    } catch (InvalidTokenException e) {
      // 이미 무효화된 토큰도 로그아웃 성공으로 처리한다.
    }
  }
}
