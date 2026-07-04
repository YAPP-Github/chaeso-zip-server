package chaeso.zip.server.common.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.user.application.AuthService;
import chaeso.zip.server.user.application.dto.LoginCommand;
import chaeso.zip.server.user.application.dto.TokenResponse;
import chaeso.zip.server.user.presentation.AuthController;
import chaeso.zip.server.user.presentation.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실제 SecurityFilterChain을 적용해 공개 경로와 보호 경로의 인가 정책을 검증한다.
 * 명시된 Auth POST 경로만 공개하고, 그 외 경로와 HTTP 메서드는 인증을 요구한다.
 */
@WebMvcTest({AuthController.class, SecurityConfigIntegrationTest.ProtectedAuthProbeController.class})
@Import({SecurityConfig.class, SecurityConfigIntegrationTest.TestBeans.class})
class SecurityConfigIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private AuthService authService;
  @MockitoBean private JwtTokenProvider jwtTokenProvider;

  @TestConfiguration
  static class TestBeans {
    @Bean
    CorsProperties corsProperties() {
      return new CorsProperties("http://localhost:3000");
    }
  }

  @RestController
  static class ProtectedAuthProbeController {
    @GetMapping("/api/v1/auth/private-probe")
    String privateProbe() {
      return "protected";
    }

    @GetMapping("/api/v1/auth/login")
    String loginWithWrongMethod() {
      return "protected";
    }
  }

  @Test
  @DisplayName("인증 없이도 /api/v1/auth/login은 필터체인을 통과한다")
  void authPathIsPublic() throws Exception {
    given(authService.login(any(LoginCommand.class)))
        .willReturn(new TokenResponse("access", "refresh"));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new LoginRequest("user@chaeso.zip", "P@ssw0rd!"))))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("public이 아닌 경로는 토큰 없이 401을 반환한다")
  void protectedPathReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("명시하지 않은 신규 auth 경로는 기본적으로 보호한다")
  void unspecifiedAuthPathIsProtected() throws Exception {
    mockMvc.perform(get("/api/v1/auth/private-probe"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("공개 auth 경로라도 POST가 아니면 기본적으로 보호한다")
  void authPathWithWrongMethodIsProtected() throws Exception {
    mockMvc.perform(get("/api/v1/auth/login"))
        .andExpect(status().isUnauthorized());
  }
}