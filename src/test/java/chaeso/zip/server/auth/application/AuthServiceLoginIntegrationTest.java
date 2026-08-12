package chaeso.zip.server.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import chaeso.zip.server.auth.application.dto.LoginCommand;
import chaeso.zip.server.auth.domain.AuthBusinessException;
import chaeso.zip.server.auth.domain.AuthErrorCode;
import chaeso.zip.server.auth.domain.AuthIdentity;
import chaeso.zip.server.auth.domain.AuthIdentityRepository;
import chaeso.zip.server.auth.domain.AuthProvider;
import chaeso.zip.server.support.UserFixture;
import chaeso.zip.server.support.redis.EmbeddedRedisTest;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserRepository;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 로그인 트랜잭션 경계와 로그인 기록의 실제 커밋을 검증한다. */
@SpringBootTest(properties = "spring.data.redis.port=16388")
@EmbeddedRedisTest(port = 16388)
class AuthServiceLoginIntegrationTest {

  @Autowired
  private AuthService authService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private AuthIdentityRepository authIdentityRepository;

  @MockitoSpyBean
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

  @Test
  @DisplayName("비밀번호 검증은 DB 트랜잭션 밖에서 수행한다")
  void login_verifiesPasswordOutsideTransaction() {
    User user = userRepository.save(UserFixture.user("invalid-login@chaeso.zip"));
    authIdentityRepository.save(AuthIdentity.createLocal(user.getId(), "encoded-password"));
    AtomicReference<Boolean> transactionActiveDuringPasswordCheck = new AtomicReference<>();
    doAnswer(invocation -> {
      transactionActiveDuringPasswordCheck.set(
          TransactionSynchronizationManager.isActualTransactionActive());
      return false;
    }).when(passwordEncoder).matches("wrong-password", "encoded-password");

    LoginCommand command = new LoginCommand("invalid-login@chaeso.zip", "wrong-password");
    assertThatThrownBy(() -> authService.login(command))
        .isInstanceOf(AuthBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    assertThat(transactionActiveDuringPasswordCheck).hasValue(false);
  }
}
