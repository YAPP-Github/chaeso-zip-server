package chaeso.zip.server.auth.infrastructure.oauth;

/**
 * Google idToken 검증 결과. {@code name} 은 구글 계정에 없으면 null.
 * {@link GoogleIdTokenVerifier} 가 미검증 이메일을 거절하므로 {@code email_verified} 는 담지 않는다.
 */
public record GoogleIdTokenInfo(String sub, String email, String name) {
}
