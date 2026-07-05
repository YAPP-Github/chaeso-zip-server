package chaeso.zip.server.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.common.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class UserRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  private static User newUser(String email) {
    return User.create(email, "채소러버", "채소컴퍼니", Occupation.DEVELOPMENT, true, "v1.0", false);
  }

  @Test
  @DisplayName("저장하면 uuid 식별자와 감사필드가 채워지고 이메일로 조회된다")
  void saveAndFindByEmail() {
    User saved = userRepository.save(newUser("user@chaeso.zip"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).isPresent();
    assertThat(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).isTrue();
  }

  @Test
  @DisplayName("이메일은 대소문자와 관계없이 조회하고 존재 여부를 확인한다")
  void findByEmailIgnoringCase() {
    userRepository.save(newUser("User@Chaeso.Zip"));

    assertThat(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).isPresent();
    assertThat(userRepository.existsByEmailAndDeletedAtIsNull("USER@CHAESO.ZIP")).isTrue();
  }

  @Test
  @DisplayName("회사명 없이 회원을 저장할 수 없다")
  void saveWithoutCompanyName() {
    User user = User.create("user@chaeso.zip", "채소러버", null, Occupation.DEVELOPMENT, true,
        "v1.0", false);

    assertThatThrownBy(() -> userRepository.saveAndFlush(user))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
