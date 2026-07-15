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
 * 로그인 유스케이스의 영속성 통합 테스트. login 의 {@code @Transactional} 이 {@code recordLogin} 변경을
 * H2 에 실제로 커밋하는지 확인한다.
 *
 * <p>테스트 메서드에는 {@code @Transactional} 을 붙이지 않는다. 붙일시 영속성 컨텍스트가 공유돼서
 * 서비스에 {@code @Transactional} 이 빠져 있어도 테스트가 통과된다.
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
