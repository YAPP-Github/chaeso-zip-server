package chaeso.zip.server.auth.infrastructure.security;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.infrastructure.jwt.JwtTokenProvider;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 토큰이 없거나 유효하지 않으면 인증 없이 다음 필터로 넘기고, 이후 인가 단계에서 401을 응답한다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;

  @Override
  @SuppressWarnings("NullableProblems")
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String token = jwtTokenProvider.resolveToken(request);
    if (token != null) {
      authenticate(token);
    }
    filterChain.doFilter(request, response);
  }

  private void authenticate(String token) {
    Optional<UserPrincipal> principal = jwtTokenProvider.tryParseAccess(token);
    if (principal.isEmpty()) {
      log.debug("Access token rejected");
      SecurityContextHolder.clearContext();
      return;
    }
    User user = userRepository.findByIdAndDeletedAtIsNull(principal.get().userId()).orElse(null);
    // 탈퇴로 세션 버전이 올라간 뒤에도 이전 토큰이 계속 인증되지 않도록 같이 검사한다.
    if (user == null || user.getSessionVersion() != principal.get().sessionVersion()) {
      SecurityContextHolder.clearContext();
      return;
    }
    var authentication = UsernamePasswordAuthenticationToken.authenticated(
        principal.get(), null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
