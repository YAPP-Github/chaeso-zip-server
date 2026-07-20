package chaeso.zip.server.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import chaeso.zip.server.auth.domain.AuthBusinessException;
import chaeso.zip.server.auth.domain.AuthErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.json.webtoken.JsonWebSignature;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Google idToken 검증 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class GoogleIdTokenVerifierTest {

  @Mock
  private com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier googleVerifier;

  private GoogleIdTokenVerifier verifier;

  @BeforeEach
  void setUp() {
    verifier = new GoogleIdTokenVerifier(googleVerifier);
  }

  private static GoogleIdToken token(Boolean emailVerified, String name) {
    GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
    payload.setSubject("google-sub-123");
    payload.setEmail("user@chaeso.zip");
    payload.setEmailVerified(emailVerified);
    payload.set("name", name);
    return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
  }

  private static Stream<Exception> libraryFailures() {
    return Stream.of(
        new GeneralSecurityException("bad signature"),
        new IOException("jwks unreachable"),
        new IllegalArgumentException("not a jwt"));
  }

  @Test
  @DisplayName("검증에 성공하면 sub/email/name 을 추출한다")
  void verify_success() throws Exception {
    given(googleVerifier.verify("valid-token")).willReturn(token(true, "홍길동"));

    GoogleIdTokenInfo info = verifier.verify("valid-token");

    assertThat(info.sub()).isEqualTo("google-sub-123");
    assertThat(info.email()).isEqualTo("user@chaeso.zip");
    assertThat(info.name()).isEqualTo("홍길동");
  }

  @Test
  @DisplayName("구글 계정에 이름이 없으면 name 은 null 이다")
  void verify_withoutName() throws Exception {
    given(googleVerifier.verify("valid-token")).willReturn(token(true, null));

    assertThat(verifier.verify("valid-token").name()).isNull();
  }

  @Test
  @DisplayName("clientId 가 비어 있으면 기동 시점에 실패한다")
  void construct_blankClientId() {
    GoogleProperties properties = new GoogleProperties(" ", Duration.ofMinutes(10));

    assertThatThrownBy(() -> new GoogleIdTokenVerifier(properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GOOGLE_CLIENT_ID");
  }

  @Test
  @DisplayName("만료/aud 불일치/서명 위조로 검증이 실패하면 AUTH-009를 던진다")
  void verify_rejectedToken() throws Exception {
    given(googleVerifier.verify("bad-token")).willReturn(null);

    assertThatThrownBy(() -> verifier.verify("bad-token"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.GOOGLE_AUTH_FAILED);
  }

  @ParameterizedTest
  @MethodSource("libraryFailures")
  @DisplayName("검증 라이브러리가 예외를 던지면 AUTH-009를 던진다")
  void verify_libraryFailure(Exception failure) throws Exception {
    willThrow(failure).given(googleVerifier).verify("bad-token");

    assertThatThrownBy(() -> verifier.verify("bad-token"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.GOOGLE_AUTH_FAILED);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = false)
  @DisplayName("서명은 유효해도 이메일이 미검증이면 AUTH-009로 거절한다")
  void verify_emailNotVerified(Boolean emailVerified) throws Exception {
    given(googleVerifier.verify("unverified-email")).willReturn(token(emailVerified, "홍길동"));

    assertThatThrownBy(() -> verifier.verify("unverified-email"))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode").isEqualTo(AuthErrorCode.GOOGLE_AUTH_FAILED);
  }
}
