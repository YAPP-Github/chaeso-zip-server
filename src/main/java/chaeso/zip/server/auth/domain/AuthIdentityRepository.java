package chaeso.zip.server.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {

    Optional<AuthIdentity> findByUserIdAndProvider(UUID userId, AuthProvider provider);

    Optional<AuthIdentity> findByProviderAndProviderUid(AuthProvider provider, String providerUid);

    List<AuthIdentity> findAllByUserId(UUID userId);
}
