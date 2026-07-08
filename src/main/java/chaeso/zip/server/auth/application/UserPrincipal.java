package chaeso.zip.server.auth.application;

import java.util.UUID;

/**
 * 인증된 사용자. Access Token 검증을 통과한 요청에서 SecurityContext의 principal로 사용한다.
 */
public record UserPrincipal(UUID userId) {
}
