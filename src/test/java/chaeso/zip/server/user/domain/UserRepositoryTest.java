package chaeso.zip.server.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.common.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class UserRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  private static User newUser(String email) {
    return User.create(email, "채소러버", EmploymentStatus.EMPLOYEE, "채소컴퍼니", Occupation.DEVELOPMENT,
        true, "v1.0", false);
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
  @DisplayName("같은 이메일을 중복 저장하면 제약 위반 예외가 발생한다")
  void duplicateEmail_throws() {
    userRepository.save(newUser("dup@chaeso.zip"));

    assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("dup@chaeso.zip")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
