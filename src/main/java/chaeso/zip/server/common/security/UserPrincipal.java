package chaeso.zip.server.common.security;

import java.util.UUID;

/**
 * 인증된 사용자 주체. 엔티티를 노출하지 않기 위해 식별자만 담는다.
 */
public record UserPrincipal(UUID userId) {
}
