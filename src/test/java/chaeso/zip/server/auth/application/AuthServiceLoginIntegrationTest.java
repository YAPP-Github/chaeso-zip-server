package chaeso.zip.server.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.auth.application.dto.LoginCommand;
import chaeso.zip.server.auth.domain.AuthIdentity;
import chaeso.zip.server.auth.domain.AuthIdentityRepository;
import chaeso.zip.server.auth.domain.AuthProvider;
import chaeso.zip.server.support.EmbeddedRedisConfig;
import chaeso.zip.server.support.UserFixture;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * login 은 {@code @Transactional} 없이 {@code userRepository.save} 로만 {@code recordLogin} 을
 * 커밋한다. 그게 실제로 커밋되는지 확인한다.
 *
 * <p>테스트에 {@code @Transactional} 을 붙이면 영속성 컨텍스트가 공유돼 커밋 없이도 통과한다.
 */
@SpringBootTest
@Import(EmbeddedRedisConfig.class)
class AuthServiceLoginIntegrationTest {

  @Autowired
  private AuthService authService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private AuthIdentityRepository authIdentityRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @AfterEach
  void tearDown() {
    authIdentityRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  @DisplayName("로그인 성공 시 마지막 로그인 정보가 실제로 커밋되어 영속화된다")
  void login_persistsLastLogin() {
    User user = userRepository.save(UserFixture.user("login@chaeso.zip"));
    authIdentityRepository.save(
        AuthIdentity.createLocal(user.getId(), passwordEncoder.encode("P@ssw0rd!")));

    authService.login(new LoginCommand("login@chaeso.zip", "P@ssw0rd!"));

    User reloaded = userRepository.findByEmailAndDeletedAtIsNull("login@chaeso.zip").orElseThrow();
    assertThat(reloaded.getLastLoginAt()).isNotNull();
    assertThat(reloaded.getLastLoginProvider()).isEqualTo(AuthProvider.LOCAL);
  }
}
