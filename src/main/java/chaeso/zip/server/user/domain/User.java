package chaeso.zip.server.user.domain;

import chaeso.zip.server.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 프로필 애그리거트 루트. 로그인 방법(provider/password_hash)은 {@link AuthIdentity}로 분리한다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

  @Column(nullable = false)
  private String email;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Column(nullable = false, length = 50)
  private String nickname;

  @Column(name = "company_name", nullable = false)
  private String companyName;

  @Column(name = "profile_image_url", length = 500)
  private String profileImageUrl;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private Occupation occupation;

  @Column(name = "last_login_at")
  private LocalDateTime lastLoginAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "last_login_provider", length = 20)
  private AuthProvider lastLoginProvider;

  @Column(name = "terms_agreed", nullable = false)
  private boolean termsAgreed;

  @Column(name = "terms_version", length = 20)
  private String termsVersion;

  @Column(name = "is_marketing_agreed", nullable = false)
  private boolean marketingAgreed;

  @Column(name = "marketing_agreed_at")
  private LocalDateTime marketingAgreedAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  private User(String email, String nickname, String companyName, Occupation occupation,
      boolean termsAgreed, String termsVersion, boolean marketingAgreed) {
    this.email = email;
    this.emailVerified = true;
    this.nickname = nickname;
    this.companyName = companyName;
    this.occupation = occupation;
    this.termsAgreed = termsAgreed;
    this.termsVersion = termsVersion;
    this.marketingAgreed = marketingAgreed;
    this.marketingAgreedAt = marketingAgreed ? LocalDateTime.now(ZoneOffset.UTC) : null;
  }

  /** 이메일 인증을 마친 뒤 가입할 때 사용한다 */
  public static User create(String email, String nickname, String companyName, Occupation occupation, boolean termsAgreed, String termsVersion, boolean marketingAgreed) {
    return new User(email, nickname, companyName, occupation, termsAgreed, termsVersion, marketingAgreed);
  }

  /** 로그인 성공 시 마지막 로그인 시각/수단을 갱신한다(파생 캐시) */
  public void recordLogin(AuthProvider provider) {
    this.lastLoginAt = LocalDateTime.now(ZoneOffset.UTC);
    this.lastLoginProvider = provider;
  }
}
