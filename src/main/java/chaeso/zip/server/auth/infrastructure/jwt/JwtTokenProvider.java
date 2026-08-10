package chaeso.zip.server.auth.infrastructure.jwt;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.domain.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Access/Refresh JWT 발급·검증. 서명/만료/타입(type claim)을 확인하고,
 * 실패 시 {@link InvalidTokenException} 으로 변환한다.
 */
@Component
public class JwtTokenProvider {

  private static final String CLAIM_TYPE = "type";
  private static final String CLAIM_FAMILY = "familyId";
  private static final String CLAIM_SESSION_VERSION = "sessionVersion";
  private static final String TYPE_ACCESS = "access";
  private static final String TYPE_REFRESH = "refresh";
  private static final long CLOCK_SKEW_SECONDS = 30;
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final SecretKey key;
  private final JwtProperties properties;
  private final Clock clock;

  public JwtTokenProvider(JwtProperties properties, Clock clock) {
    if (properties.secret() == null || properties.secret().isBlank()) {
      throw new IllegalArgumentException("JWT_SECRET 환경변수가 필요합니다.");
    }
    this.key = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(properties.secret()));
    this.properties = properties;
    this.clock = clock;
  }

  public String createAccessToken(UUID userId, int sessionVersion) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(userId.toString())
        .claim(CLAIM_SESSION_VERSION, sessionVersion)
        .claim(CLAIM_TYPE, TYPE_ACCESS)
        .issuedAt(toDate(now))
        .expiration(toDate(now.plus(properties.accessTtl())))
        .signWith(key)
        .compact();
  }

  public String createRefreshToken(UUID userId, int sessionVersion, String familyId, String jti) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(userId.toString())
        .id(jti)
        .claim(CLAIM_FAMILY, familyId)
        .claim(CLAIM_SESSION_VERSION, sessionVersion)
        .claim(CLAIM_TYPE, TYPE_REFRESH)
        .issuedAt(toDate(now))
        .expiration(toDate(now.plus(properties.refreshTtl())))
        .signWith(key)
        .compact();
  }

  public UserPrincipal parseAccess(String token) {
    Claims claims = parse(token, TYPE_ACCESS);
    return new UserPrincipal(parseUserId(claims), parseSessionVersion(claims));
  }

  /**
   * 실패 시 예외 대신 빈 값을 돌려주는 {@link #parseAccess}
   * 인증 실패를 다음 필터로 넘겨야 하는 곳에서 사용
   */
  public Optional<UserPrincipal> tryParseAccess(String token) {
    try {
      return Optional.of(parseAccess(token));
    } catch (InvalidTokenException exception) {
      return Optional.empty();
    }
  }

  /**
   * {@code Authorization: Bearer <token>} 헤더에서 토큰을 꺼낸다.
   * 없거나 형식이 다르면 {@code null}
   */
  public String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(AUTHORIZATION_HEADER);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      return null;
    }
    return header.substring(BEARER_PREFIX.length());
  }

  public RefreshTokenInfo parseRefresh(String token) {
    try {
      Claims claims = parse(token, TYPE_REFRESH);
      String familyId = claims.get(CLAIM_FAMILY, String.class);
      String jti = claims.getId();
      if (isBlank(familyId) || isBlank(jti)) {
        throw new InvalidTokenException("Refresh Token 식별자가 없습니다.");
      }
      return new RefreshTokenInfo(parseUserId(claims), parseSessionVersion(claims), familyId, jti);
    } catch (JwtException exception) {
      throw new InvalidTokenException("유효하지 않은 토큰입니다.", exception);
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private Claims parse(String token, String expectedType) {
    try {
      Claims claims = Jwts.parser()
          .verifyWith(key)
          .clock(() -> toDate(clock.instant()))
          .clockSkewSeconds(CLOCK_SKEW_SECONDS)
          .build()
          .parseSignedClaims(token)
          .getPayload();
      if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
        throw new InvalidTokenException("토큰 타입이 올바르지 않습니다.");
      }
      return claims;
    } catch (JwtException | IllegalArgumentException exception) {
      throw new InvalidTokenException("유효하지 않은 토큰입니다.", exception);
    }
  }

  private Date toDate(Instant instant) {
    return Date.from(instant);
  }

  private UUID parseUserId(Claims claims) {
    String subject = claims.getSubject();
    if (subject == null) {
      throw new InvalidTokenException("토큰 subject가 없습니다.");
    }
    try {
      return UUID.fromString(subject);
    } catch (IllegalArgumentException exception) {
      throw new InvalidTokenException("토큰 subject가 UUID 형식이 아닙니다.", exception);
    }
  }

  private int parseSessionVersion(Claims claims) {
    Object value = claims.get(CLAIM_SESSION_VERSION);
    if (!(value instanceof Number number)
        || number.doubleValue() != number.intValue()
        || number.intValue() < 0) {
      throw new InvalidTokenException("토큰 세션 버전이 올바르지 않습니다.");
    }
    return number.intValue();
  }
}
