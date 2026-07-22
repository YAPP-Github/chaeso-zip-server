package chaeso.zip.server.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.support.PostgresDataJpaTest;
import chaeso.zip.server.support.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * users 의 대소문자 무시 이메일 유니크 제약을 실제 적재된 데이터로 검증하는 통합 테스트
 */
@PostgresDataJpaTest
class UserRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @Test
  @DisplayName("저장하면 uuid 식별자와 감사필드가 채워지고 이메일로 조회된다")
  void saveAndFindByEmail() {
    User saved = userRepository.save(UserFixture.user("user@chaeso.zip"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getTermsVersion()).isEqualTo(UserFixture.consentVersions().termsVersion());
    assertThat(saved.getMarketingAgreedAt()).isNull();
    assertThat(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).isPresent();
    assertThat(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).isTrue();
  }

  @Test
  @DisplayName("이메일은 대소문자와 관계없이 조회하고 존재 여부를 확인한다")
  void findByEmailIgnoringCase() {
    userRepository.save(UserFixture.user("User@Chaeso.Zip"));

    assertThat(userRepository.findByEmailAndDeletedAtIsNull("user@chaeso.zip")).isPresent();
    assertThat(userRepository.existsByEmailAndDeletedAtIsNull("USER@CHAESO.ZIP")).isTrue();
  }

  @Test
  @DisplayName("회사명 없이 회원을 저장할 수 없다")
  void saveWithoutCompanyName() {
    User user = User.create("user@chaeso.zip", "채소러버", null, Occupation.DEVELOPMENT, true, false,
        UserFixture.consentVersions());

    assertThatThrownBy(() -> userRepository.saveAndFlush(user))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("마케팅 수신에 동의하면 현재 광고성 동의 버전과 동의 시각이 저장된다")
  void saveWithMarketingAgreement() {
    User saved = userRepository.save(User.create("marketing@chaeso.zip", "채소러버", "채소컴퍼니",
        Occupation.DEVELOPMENT, true, true, UserFixture.consentVersions()));

    assertThat(saved.isMarketingAgreed()).isTrue();
    assertThat(saved.getTermsVersion()).isEqualTo(UserFixture.consentVersions().termsVersion());
    assertThat(saved.getMarketingAgreedAt()).isNotNull();
  }

  @Test
  @DisplayName("대소문자만 다른 이메일로 중복 저장하면 부분 유니크 인덱스 위반으로 실패한다")
  void rejectsDuplicateActiveEmailCaseInsensitive() {
    userRepository.saveAndFlush(UserFixture.user("dup-index@chaeso.zip"));

    User duplicateUser = UserFixture.user("DUP-INDEX@Chaeso.Zip");
    assertThatThrownBy(() -> userRepository.saveAndFlush(duplicateUser))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
