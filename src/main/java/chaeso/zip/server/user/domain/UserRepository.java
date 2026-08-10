package chaeso.zip.server.user.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdForUpdate(@Param("id") UUID id);

  /** 탈퇴한 회원도 포함해서 조회한다. 재가입/재로그인을 막을 때 쓴다. */
  @Query("select u from User u where lower(u.email) = lower(:email)")
  Optional<User> findByEmail(@Param("email") String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where lower(u.email) = lower(:email)")
  Optional<User> findByEmailForUpdate(@Param("email") String email);

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
