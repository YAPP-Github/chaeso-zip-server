package chaeso.zip.server.auth.infrastructure.jwt;

import java.util.UUID;

/**
 * Refresh Token 파싱 결과.
 */
public record RefreshTokenInfo(UUID userId, int sessionVersion, String familyId, String jti) {

  public RefreshTokenInfo(UUID userId, String familyId, String jti) {
    this(userId, 0, familyId, jti);
  }
}
