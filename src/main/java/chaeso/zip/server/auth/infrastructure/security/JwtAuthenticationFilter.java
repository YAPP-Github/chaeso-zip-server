package chaeso.zip.server.auth.infrastructure.security;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.infrastructure.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer Access Token으로 요청을 인증한다. 토큰이 없거나 유효하지 않으면
 * 인증 없이 다음 필터로 넘기며, 이후 인가 단계에서 401로 처리된다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider jwtTokenProvider;

  @Override
  @SuppressWarnings("NullableProblems")
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String token = resolveToken(request);
    if (token != null) {
      authenticate(token);
    }
    filterChain.doFilter(request, response);
  }

  private void authenticate(String token) {
    try {
      UserPrincipal principal = jwtTokenProvider.parseAccess(token);
      var authentication = UsernamePasswordAuthenticationToken.authenticated(
          principal, null, List.of());
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (RuntimeException exception) {
      log.debug("Access token rejected: {}", exception.getMessage());
      SecurityContextHolder.clearContext();
    }
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      return null;
    }
    return header.substring(BEARER_PREFIX.length());
  }
}
