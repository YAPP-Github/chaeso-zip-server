package chaeso.zip.server.auth.application;

import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.domain.AuthBusinessException;
import chaeso.zip.server.auth.domain.AuthErrorCode;
import chaeso.zip.server.auth.domain.AuthProvider;
import chaeso.zip.server.auth.infrastructure.jwt.JwtProperties;
import chaeso.zip.server.auth.infrastructure.jwt.JwtTokenProvider;
import chaeso.zip.server.auth.infrastructure.jwt.RefreshTokenStore;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 성공 후 사용자 상태를 확정하고 세션을 발급한다. */
@Service
@RequiredArgsConstructor
public class AuthSessionService {

  private final UserRepository userRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final JwtProperties jwtProperties;
  private final RefreshTokenStore refreshTokenStore;
  private final Clock clock;

  /** 비밀번호 검증을 마친 로컬 사용자를 잠근 뒤 탈퇴 여부를 확인하고 세션을 발급한다. */
  @Transactional
  public TokenResponse openLocalSession(UUID authenticatedUserId) {
    User user = userRepository.findByIdForUpdate(authenticatedUserId)
        .orElseThrow(() -> new AuthBusinessException(AuthErrorCode.INVALID_CREDENTIALS));
    if (user.getDeletedAt() != null) {
      throw new AuthBusinessException(AuthErrorCode.ACCOUNT_DELETION_IN_PROGRESS);
    }
    return openSession(user, AuthProvider.LOCAL);
  }

  /** 새 refresh family를 열고 토큰 쌍을 발급한다. */
  public TokenResponse openSession(User user, AuthProvider provider) {
    UUID userId = user.getId();
    String familyId = UUID.randomUUID().toString();
    String jti = UUID.randomUUID().toString();
    Duration refreshTtl = refreshTokenStore.save(userId, familyId, jti);

    user.recordLogin(provider, LocalDateTime.now(clock));
    userRepository.save(user);

    return createTokenResponse(userId, user.getSessionVersion(), familyId, jti, refreshTtl);
  }

  /** 회전된 세션 식별자로 새 토큰 쌍을 만든다. */
  public TokenResponse createTokenResponse(UUID userId, int sessionVersion, String familyId,
      String jti, Duration refreshTtl) {
    return new TokenResponse(
        jwtTokenProvider.createAccessToken(userId, sessionVersion),
        jwtTokenProvider.createRefreshToken(userId, sessionVersion, familyId, jti),
        jwtProperties.accessTtl().toSeconds(),
        refreshTtl.toSeconds());
  }
}
