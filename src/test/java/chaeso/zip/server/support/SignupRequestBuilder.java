package chaeso.zip.server.support;

import chaeso.zip.server.user.domain.EmploymentStatus;
import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.user.presentation.dto.SignupRequest;

/**
 * SignupRequest 테스트 데이터 빌더. 기본은 유효한 요청이고, 각 테스트는 검증하려는 필드만 덮어쓴다.
 */
public final class SignupRequestBuilder {

  private String email = "user@chaeso.zip";
  private String password = "P@ssw0rd!";
  private String nickname = "닉";
  private EmploymentStatus employmentStatus = EmploymentStatus.EMPLOYEE;
  private String companyName = null;
  private Occupation occupation = null;
  private boolean termsAgreed = true;
  private String termsVersion = "v1.0";
  private boolean marketingAgreed = false;

  private SignupRequestBuilder() {
  }

  public static SignupRequestBuilder aSignupRequest() {
    return new SignupRequestBuilder();
  }

  public SignupRequestBuilder email(String email) {
    this.email = email;
    return this;
  }

  public SignupRequestBuilder password(String password) {
    this.password = password;
    return this;
  }

  public SignupRequestBuilder termsAgreed(boolean termsAgreed) {
    this.termsAgreed = termsAgreed;
    return this;
  }

  public SignupRequest build() {
    return new SignupRequest(email, password, nickname, employmentStatus, companyName, occupation,
        termsAgreed, termsVersion, marketingAgreed);
  }
}
