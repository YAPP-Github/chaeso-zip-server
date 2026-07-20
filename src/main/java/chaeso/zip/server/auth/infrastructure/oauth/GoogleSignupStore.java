package chaeso.zip.server.auth.infrastructure.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * "idToken 검증 완료 · 아직 가입 안 됨" 중간 상태를 {@code google-signup:{signupToken}} 키에 보관한다.
 * 이메일·sub 는 서버에만 두고 클라이언트에는 불투명 랜덤 티켓만 노출한다. 폼 작성 중 이탈하면
 * 아무 행도 만들어지지 않은 채 TTL 로 소멸하므로 정리 배치가 없다.
 */
@Component
public class GoogleSignupStore {

  private static final String PREFIX = "google-signup:";
  private static final int TOKEN_BYTES = 32;

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final Duration signupTtl;
  private final SecureRandom secureRandom = new SecureRandom();

  public GoogleSignupStore(StringRedisTemplate redis, ObjectMapper objectMapper,
      GoogleProperties properties) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.signupTtl = properties.signupTtl();
  }

  /** 검증된 구글 클레임을 저장하고 최종가입 때 되받을 티켓을 발급한다. */
  public String save(GoogleIdTokenInfo info) {
    String signupToken = generateToken();
    redis.opsForValue().set(PREFIX + signupToken, serialize(info), signupTtl);
    return signupToken;
  }

  /** 티켓으로 보관된 클레임을 조회한다. 삭제하지 않는다 — 가입이 실제로 끝난 뒤에만 {@link #delete} 한다. */
  public Optional<GoogleIdTokenInfo> find(String signupToken) {
    String json = redis.opsForValue().get(PREFIX + signupToken);
    return json == null ? Optional.empty() : Optional.of(deserialize(json));
  }

  /** 가입 성공 시 티켓을 폐기해 재사용을 막는다. */
  public void delete(String signupToken) {
    redis.delete(PREFIX + signupToken);
  }

  private GoogleIdTokenInfo deserialize(String json) {
    try {
      return objectMapper.readValue(json, GoogleIdTokenInfo.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("구글 가입 정보를 역직렬화하지 못했습니다.", exception);
    }
  }

  private String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String serialize(GoogleIdTokenInfo info) {
    try {
      return objectMapper.writeValueAsString(info);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("구글 가입 정보를 직렬화하지 못했습니다.", exception);
    }
  }
}
