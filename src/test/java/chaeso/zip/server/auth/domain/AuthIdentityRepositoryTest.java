package chaeso.zip.server.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.common.config.JpaAuditingConfig;
import chaeso.zip.server.common.config.QuerydslConfig;
import chaeso.zip.server.support.UserFixture;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class AuthIdentityRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthIdentityRepository authIdentityRepository;

    @Test
    @DisplayName("저장하면 uuid 식별자가 채워지고 (userId, provider) 로 조회된다")
    void saveAndFindByUserIdAndProvider() {
        User user = userRepository.save(UserFixture.user("user@chaeso.zip"));

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
        User user = userRepository.save(UserFixture.user("dup2@chaeso.zip"));
        authIdentityRepository.save(AuthIdentity.createLocal(user.getId(), "hashed-1"));
        AuthIdentity duplicateIdentity = AuthIdentity.createLocal(user.getId(), "hashed-2");

        assertThatThrownBy(() -> authIdentityRepository.saveAndFlush(duplicateIdentity))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("provider 와 provider_uid 로 소유 유저와 무관하게 조회된다")
    void findByProviderAndProviderUid() {
        User user = userRepository.save(UserFixture.user("sub-lookup@chaeso.zip"));
        authIdentityRepository.save(AuthIdentity.createGoogle(user.getId(), "google-sub-x"));

        assertThat(authIdentityRepository.findByProviderAndProviderUid(AuthProvider.GOOGLE, "google-sub-x"))
                .isPresent()
                .get()
                .extracting(AuthIdentity::getUserId)
                .isEqualTo(user.getId());
    }
}
