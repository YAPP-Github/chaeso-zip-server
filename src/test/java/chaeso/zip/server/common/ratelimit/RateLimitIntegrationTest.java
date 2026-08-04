package chaeso.zip.server.common.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import redis.embedded.RedisServer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

  private static final int PORT = 16386;
  private static RedisServer redisServer;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.port", () -> PORT);
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @BeforeAll
  static void startRedis() throws IOException {
    redisServer = RedisServer.newRedisServer().port(PORT).build();
    redisServer.start();
  }

  @AfterAll
  static void stopRedis() throws IOException {
    redisServer.stop();
  }

  @AfterEach
  void flush() {
    if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
      redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }
  }

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
