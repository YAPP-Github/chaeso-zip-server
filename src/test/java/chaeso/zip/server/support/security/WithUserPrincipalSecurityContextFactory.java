package chaeso.zip.server.support.security;

import chaeso.zip.server.auth.application.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/** {@link WithUserPrincipal} 설정을 읽어 {@link UserPrincipal} 인증 정보를 만든다. */
public final class WithUserPrincipalSecurityContextFactory implements
    WithSecurityContextFactory<WithUserPrincipal> {

  @Override
  public SecurityContext createSecurityContext(WithUserPrincipal annotation) {
    UserPrincipal principal = new UserPrincipal(UUID.fromString(annotation.userId()));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    return context;
  }
}
