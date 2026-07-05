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
class AuthIdentityRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthIdentityRepository authIdentityRepository;

    @Test
    @DisplayName("저장하면 uuid 식별자가 채워지고 (userId, provider) 로 조회된다")
    void saveAndFindByUserIdAndProvider() {
        User user = userRepository.save(User.create("user@chaeso.zip", "채소러버",
                "채소컴퍼니", Occupation.DEVELOPMENT, true, "v1.0", false));

        AuthIdentity saved = authIdentityRepository.save(AuthIdentity.createLocal(user.getId(), "hashed"));

        assertThat(saved.getId()).isNotNull();
        assertThat(authIdentityRepository.findByUserIdAndProvider(user.getId(), AuthProvider.LOCAL))
                .isPresent()
                .get()
                .extracting(AuthIdentity::getPasswordHash)
                .isEqualTo("hashed");
    }

    @Test
    @DisplayName("같은 유저에 같은 provider 를 중복 저장하면 제약 위반 예외가 발생한다")
    void duplicateUserProvider_throws() {
        User user = userRepository.save(User.create("dup2@chaeso.zip", "닉",
                "채소컴퍼니", Occupation.DEVELOPMENT, true, "v1.0", false));
        authIdentityRepository.save(AuthIdentity.createLocal(user.getId(), "hashed-1"));
        AuthIdentity duplicateIdentity = AuthIdentity.createLocal(user.getId(), "hashed-2");

        assertThatThrownBy(() -> authIdentityRepository.saveAndFlush(duplicateIdentity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
