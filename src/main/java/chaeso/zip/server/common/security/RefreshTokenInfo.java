package chaeso.zip.server.common.security;

import java.util.UUID;

/**
 * Refresh 토큰 파싱 결과.
 */
public record RefreshTokenInfo(UUID userId, String familyId, String jti) {
}
