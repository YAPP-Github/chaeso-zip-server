package chaeso.zip.server.auth.infrastructure.oauth;

import chaeso.zip.server.auth.domain.AuthBusinessException;
import chaeso.zip.server.auth.domain.AuthErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Google idToken 검증. 서명(JWKS)/aud/iss/exp 는 {@code google-api-client} 가 확인하고,
 * 실패는 사유를 구분하지 않고 {@link AuthErrorCode#GOOGLE_AUTH_FAILED} 로 변환한다.
 */
@Component
public class GoogleIdTokenVerifier {

  private static final List<String> ISSUERS =
      List.of("accounts.google.com", "https://accounts.google.com");
  private static final String CLAIM_NAME = "name";

  private final com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier verifier;

  @Autowired
  public GoogleIdTokenVerifier(GoogleProperties properties) {
    this(build(properties));
  }

  GoogleIdTokenVerifier(
      com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier verifier) {
    this.verifier = verifier;
  }

  private static com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier build(
      GoogleProperties properties) {
    if (properties.clientId() == null || properties.clientId().isBlank()) {
      throw new IllegalArgumentException("GOOGLE_CLIENT_ID 환경변수가 필요합니다.");
    }
    return new com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.Builder(
        new NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(List.of(properties.clientId()))
        .setIssuers(ISSUERS)
        .build();
  }

  public GoogleIdTokenInfo verify(String idToken) {
    GoogleIdToken token;
    try {
      token = verifier.verify(idToken);
    } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) {
      throw new AuthBusinessException(AuthErrorCode.GOOGLE_AUTH_FAILED);
    }
    if (token == null) {
      throw new AuthBusinessException(AuthErrorCode.GOOGLE_AUTH_FAILED);
    }

    GoogleIdToken.Payload payload = token.getPayload();
    if (!Boolean.TRUE.equals(payload.getEmailVerified())
        || payload.getEmail() == null || payload.getEmail().isBlank()) {
      throw new AuthBusinessException(AuthErrorCode.GOOGLE_AUTH_FAILED);
    }
    return new GoogleIdTokenInfo(
        payload.getSubject(), payload.getEmail(), (String) payload.get(CLAIM_NAME));
  }
}
