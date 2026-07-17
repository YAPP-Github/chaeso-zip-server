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
import chaeso.zip.server.auth.domain.InvalidTokenException;
import chaeso.zip.server.auth.infrastructure.jwt.JwtProperties;
import chaeso.zip.server.auth.infrastructure.jwt.JwtTokenProvider;
import chaeso.zip.server.auth.infrastructure.jwt.RefreshTokenInfo;
import chaeso.zip.server.auth.infrastructure.jwt.RefreshTokenStore;
import chaeso.zip.server.auth.infrastructure.jwt.RefreshTokenStore.RotateOutcome;
import chaeso.zip.server.auth.infrastructure.jwt.RefreshTokenStore.RotateResult;
import chaeso.zip.server.auth.infrastructure.mail.VerificationMailSender;
import chaeso.zip.server.auth.infrastructure.verification.EmailVerificationCodeStore;
import chaeso.zip.server.user.application.ConsentProperties;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserRepository;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Duration;
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
  private final RefreshTokenStore refreshTokenStore;

  private final SecureRandom secureRandom = new SecureRandom();

  /** 미가입 계정으로 로그인해도 응답 시간이 달라지지 않도록, 비밀번호 비교에 대신 쓰는 해시. */
  private String dummyPasswordHash;

  @PostConstruct
  void initDummyPasswordHash() {
    dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

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
  public TokenResponse login(LoginCommand command) {
    String email = normalizeEmail(command.email());
    User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
    AuthIdentity identity = user == null ? null
        : authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL)
            .orElse(null);
    String passwordHash = identity == null ? null : identity.getPasswordHash();

    boolean hasHash = passwordHash != null && !passwordHash.isBlank();
    boolean passwordMatches =
        passwordEncoder.matches(command.rawPassword(), hasHash ? passwordHash : dummyPasswordHash);
    if (!hasHash || !passwordMatches) {
      throw new AuthBusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    UUID userId = user.getId();
    String familyId = UUID.randomUUID().toString();
    String jti = UUID.randomUUID().toString();
    Duration refreshTtl = refreshTokenStore.save(userId, familyId, jti);

    user.recordLogin(AuthProvider.LOCAL);
    userRepository.save(user);

    return tokenResponse(userId, familyId, jti, refreshTtl);
  }

  @Override
  public TokenResponse reissue(String refreshToken) {
    RefreshTokenInfo info = parseRefreshToken(refreshToken);
    String newJti = UUID.randomUUID().toString();
    RotateOutcome outcome =
        refreshTokenStore.rotate(info.userId(), info.familyId(), info.jti(), newJti);
    if (outcome.result() == RotateResult.REUSED) {
      throw new AuthBusinessException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
    }
    if (outcome.result() != RotateResult.ROTATED) {
      throw new AuthBusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    return tokenResponse(info.userId(), info.familyId(), newJti, outcome.ttl());
  }

  @Override
  public void logout(UUID userId, String refreshToken) {
    RefreshTokenInfo info = parseRefreshToken(refreshToken);
    if (!userId.equals(info.userId())) {
      throw new AuthBusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
    refreshTokenStore.revoke(info.userId(), info.familyId());
  }

  /**
   * {@code refreshTtl} 은 Redis 키에 실제로 걸린 TTL 이다. 절대만료가 가까우면 refresh-ttl 보다
   * 짧아지므로 설정값이 아니라 이 값을 내려준다.
   */
  private TokenResponse tokenResponse(UUID userId, String familyId, String jti,
      Duration refreshTtl) {
    return new TokenResponse(
        jwtTokenProvider.createAccessToken(userId),
        jwtTokenProvider.createRefreshToken(userId, familyId, jti),
        jwtProperties.accessTtl().toSeconds(),
        refreshTtl.toSeconds());
  }

  /**
   * refresh 토큰을 파싱한다. {@link JwtTokenProvider} 는 서명/만료 실패를 AUTH-001 로 던지지만
   * 재발급/로그아웃 경로에서는 refresh 전용 코드인 AUTH-004 로 바꿔 응답한다.
   */
  private RefreshTokenInfo parseRefreshToken(String refreshToken) {
    try {
      return jwtTokenProvider.parseRefresh(refreshToken);
    } catch (InvalidTokenException exception) {
      throw new AuthBusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
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
