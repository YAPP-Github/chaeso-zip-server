package chaeso.zip.server.user.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.auth.application.AuthService;
import chaeso.zip.server.auth.application.dto.LoginCommand;
import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.domain.AuthIdentity;
import chaeso.zip.server.auth.domain.AuthIdentityRepository;
import chaeso.zip.server.support.UserFixture;
import chaeso.zip.server.support.redis.EmbeddedRedisTest;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.data.redis.port=16387")
@AutoConfigureMockMvc
@EmbeddedRedisTest(port = 16387)
class UserWithdrawalIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private AuthIdentityRepository authIdentityRepository;

  @Autowired
  private AuthService authService;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @AfterEach
  void tearDown() {
    authIdentityRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("회원 탈퇴 직후 탈퇴 전에 발급한 Access Token은 보호 API에서 거절된다")
  void withdrawalImmediatelyBlocksExistingAccessToken() throws Exception {
    User user = userRepository.save(UserFixture.user("withdraw@chaeso.zip"));
    authIdentityRepository.save(
        AuthIdentity.createLocal(user.getId(), passwordEncoder.encode("P@ssw0rd!")));
    TokenResponse oldSession = authService.login(
        new LoginCommand("withdraw@chaeso.zip", "P@ssw0rd!"));

    mockMvc.perform(delete("/api/v1/users/me")
            .header("Authorization", "Bearer " + oldSession.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.withdrawnAt").exists());

    mockMvc.perform(get("/api/v1/users/me")
            .header("Authorization", "Bearer " + oldSession.accessToken()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("C-004"));
    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"refreshToken": "%s"}
                """.formatted(oldSession.refreshToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH-004"));
  }

  @Test
  @DisplayName("탈퇴한 계정은 같은 비밀번호로도 다시 로그인할 수 없다")
  void withdrawnAccountCannotLogInAgain() throws Exception {
    User user = userRepository.save(UserFixture.user("withdrawn@chaeso.zip"));
    authIdentityRepository.save(
        AuthIdentity.createLocal(user.getId(), passwordEncoder.encode("P@ssw0rd!")));
    authService.login(new LoginCommand("withdrawn@chaeso.zip", "P@ssw0rd!"));
    user.withdraw(LocalDateTime.now(ZoneOffset.UTC));
    userRepository.saveAndFlush(user);

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email": "withdrawn@chaeso.zip", "password": "P@ssw0rd!"}
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("AUTH-015"));
  }
}
