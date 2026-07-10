package chaeso.zip.server.auth.infrastructure.security;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.infrastructure.jwt.JwtTokenProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigIntegrationTest.SecurityProbeController.class)
@Import({
    SecurityConfig.class,
    SecurityConfigIntegrationTest.TestBeans.class,
    SecurityConfigIntegrationTest.SecurityProbeController.class
})
class SecurityConfigIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private JwtTokenProvider jwtTokenProvider;

  @TestConfiguration
  static class TestBeans {
    @Bean
    CorsProperties corsProperties() {
      return new CorsProperties(List.of("http://localhost:3000"));
    }
  }

  @RestController
  public static class SecurityProbeController {
    @GetMapping("/api/v1/security/protected")
    String protectedApi() {
      return "protected";
    }

    @PostMapping("/api/v1/auth/signup/email-code")
    String signupEmailCodeProbe() {
      return "ok";
    }

    @PostMapping("/api/v1/auth/non-public")
    String nonPublicAuthProbe() {
      return "ok";
    }
  }

  @Test
  @DisplayName("보호 경로는 토큰 없이 접근하면 공통 JSON 401을 반환한다")
  void protectedApiRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/security/protected"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("C-004"));
  }

  @Test
  @DisplayName("유효한 Access Token이면 보호 경로에 접근할 수 있다")
  void validAccessTokenPassesProtectedApi() throws Exception {
    UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    given(jwtTokenProvider.parseAccess("valid-access-token"))
        .willReturn(new UserPrincipal(userId));

    mockMvc.perform(get("/api/v1/security/protected")
            .header("Authorization", "Bearer valid-access-token"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("회원가입 공개 경로는 토큰 없이 접근할 수 있다")
  void signupPathIsPublic() throws Exception {
    mockMvc.perform(post("/api/v1/auth/signup/email-code"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("허용 목록에 없는 auth 경로는 토큰 없이 접근하면 401을 반환한다")
  void nonListedAuthPathRequiresAuthentication() throws Exception {
    mockMvc.perform(post("/api/v1/auth/non-public"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("C-004"));
  }

  @Test
  @DisplayName("메인 포트의 health 경로는 공개 경로로 허용하지 않는다")
  void actuatorHealthOnMainPortRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("C-004"));
  }

  @Test
  @DisplayName("메인 포트의 prometheus 경로는 공개 경로로 허용하지 않는다")
  void actuatorPrometheusOnMainPortRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("C-004"));
  }

  @Test
  @DisplayName("허용된 Origin의 preflight 요청은 CORS 허용 헤더를 받는다")
  void allowedOriginPreflightReceivesCorsHeader() throws Exception {
    mockMvc.perform(options("/api/v1/security/protected")
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
  }

  @Test
  @DisplayName("허용되지 않은 Origin의 preflight 요청은 CORS 오류로 거부된다")
  void disallowedOriginPreflightIsRejected() throws Exception {
    mockMvc.perform(options("/api/v1/security/protected")
            .header("Origin", "http://evil.example.com")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }
}
