package chaeso.zip.server.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 한 유저에 연결된 로그인 방식(자체/구글 등). 유저당 provider 1개(uq_auth_identities_user_provider).
 * 생성 후 갱신되지 않아 BaseTimeEntity 대신 {@code @PrePersist} 로 created_at만 업데이트
 */
@Getter
@Entity
@Table(name = "auth_identities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthIdentity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_uid")
    private String providerUid;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private AuthIdentity(UUID userId, AuthProvider provider, String providerUid, String passwordHash) {
        this.userId = userId;
        this.provider = provider;
        this.providerUid = providerUid;
        this.passwordHash = passwordHash;
    }

    /** 로컬(이메일/비밀번호) 로그인. 비밀번호는 이미 인코딩된 해시를 받는다. */
    public static AuthIdentity createLocal(UUID userId, String passwordHash) {
        return new AuthIdentity(userId, AuthProvider.LOCAL, null, passwordHash);
    }
}