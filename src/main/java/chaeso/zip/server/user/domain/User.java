package chaeso.zip.server.user.domain;

import chaeso.zip.server.auth.domain.AuthProvider;
import chaeso.zip.server.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 프로필 애그리거트 루트. 로그인 방법(provider/password_hash)은 auth 도메인에서 따로 관리한다.
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

  @Column(name = "company_name", nullable = false, length = 50)
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

  @Column(name = "session_version", nullable = false)
  private int sessionVersion;

  private User(String email, String nickname, String companyName, Occupation occupation,
      boolean termsAgreed, boolean marketingAgreed, ConsentVersions consentVersions,
      LocalDateTime now) {
    if (!termsAgreed) {
      throw new IllegalArgumentException("Required terms must be agreed.");
    }

    this.email = email;
    this.emailVerified = true;
    this.nickname = nickname;
    this.companyName = companyName;
    this.occupation = occupation;
    this.termsAgreed = termsAgreed;
    this.termsVersion = consentVersions.termsVersion();
    this.marketingAgreed = marketingAgreed;
    this.marketingAgreedAt = marketingAgreed ? now : null;
  }

  /** 이메일 인증을 마친 뒤 가입할 때 사용한다 */
  public static User create(String email, String nickname, String companyName, Occupation occupation,
      boolean termsAgreed, boolean marketingAgreed, ConsentVersions consentVersions,
      LocalDateTime now) {
    return new User(email, nickname, companyName, occupation, termsAgreed, marketingAgreed,
        consentVersions, now);
  }

  /** 로그인 성공 시 마지막 로그인 시각/수단을 갱신한다(파생 캐시) */
  public void recordLogin(AuthProvider provider, LocalDateTime now) {
    this.lastLoginAt = now;
    this.lastLoginProvider = provider;
  }

  /** 회원을 탈퇴 상태로 전환하고 세션을 무효화한다. 이미 탈퇴했다면 아무 것도 하지 않는다. */
  public void withdraw(LocalDateTime now) {
    if (deletedAt != null) {
      return;
    }
    deletedAt = now;
    marketingAgreed = false;
    marketingAgreedAt = null;
    sessionVersion++;
  }

  /** 사용자의 직군/회사명을 수정한다 */
  public void updateProfile(String companyName, Occupation occupation) {
    this.companyName = companyName;
    this.occupation = occupation;
  }
}
