package chaeso.zip.server.user.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByIdAndDeletedAtIsNull(UUID id);

  @Query("""
      select u from User u
      where lower(u.email) = lower(:email)
        and u.deletedAt is null
      """)
  Optional<User> findByEmailAndDeletedAtIsNull(@Param("email") String email);

  @Query("""
      select (count(u) > 0) from User u
      where lower(u.email) = lower(:email)
        and u.deletedAt is null
      """)
  boolean existsByEmailAndDeletedAtIsNull(@Param("email") String email);
}
