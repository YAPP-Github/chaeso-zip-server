package chaeso.zip.server.auth.infrastructure.jwt;

import java.util.UUID;

/**
 * Refresh Token 파싱 결과.
 */
public record RefreshTokenInfo(UUID userId, String familyId, String jti) {
}
