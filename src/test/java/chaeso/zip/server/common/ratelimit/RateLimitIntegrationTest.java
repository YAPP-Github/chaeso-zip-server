package chaeso.zip.server.common.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.support.redis.EmbeddedRedisTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedRedisTest(port = RateLimitIntegrationTest.PORT)
class RateLimitIntegrationTest {

  static final int PORT = 16386;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.port", () -> PORT);
  }

  @Autowired
  private MockMvc mockMvc;

  @Nested
  @DisplayName("로그인 수단 조회 API")
  class LoginMethodsTest {

    @Test
    @DisplayName("설정된 최대 허용 횟수(30회)를 초과하여 요청하면 429 응답과 AUTH-012 에러 코드를 반환한다")
    void loginMethodsLookup_exceedsLimit_returns429() throws Exception {
      String body = "{\"email\":\"user@chaeso.zip\"}";
      for (int i = 0; i < 30; i++) {
        mockMvc.perform(post("/api/v1/auth/login/methods")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
      }

      mockMvc.perform(post("/api/v1/auth/login/methods")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isTooManyRequests())
          .andExpect(jsonPath("$.error.code").value("AUTH-012"));
    }
  }
}
