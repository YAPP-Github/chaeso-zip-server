package chaeso.zip.server.auth.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.user.domain.Occupation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignupRequestValidationTest {

  private static Validator validator;

  @BeforeAll
  static void setUp() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  private static SignupRequest valid() {
    return new SignupRequest("user@chaeso.zip", "P@ssw0rd!", "채소러버", "채소컴퍼니",
        Occupation.DEVELOPMENT, true, false);
  }

  @Test
  @DisplayName("정상 요청은 위반이 없다")
  void validRequest_noViolations() {
    assertThat(validator.validate(valid())).isEmpty();
  }

  @Test
  @DisplayName("비밀번호에 특수문자가 없으면 password 위반이 발생한다")
  void weakPassword_violatesPassword() {
    SignupRequest request = new SignupRequest("user@chaeso.zip", "password123", "채소러버", "채소컴퍼니",
        Occupation.DEVELOPMENT, true, false);

    assertThat(validator.validate(request))
        .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
  }

  @Test
  @DisplayName("필수 약관 미동의면 termsAgreed 위반이 발생한다")
  void termsNotAgreed_violatesTerms() {
    SignupRequest request = new SignupRequest("user@chaeso.zip", "P@ssw0rd!", "채소러버", "채소컴퍼니",
        Occupation.DEVELOPMENT, false, false);

    assertThat(validator.validate(request))
        .anyMatch(v -> v.getPropertyPath().toString().equals("termsAgreed"));
  }

  @Test
  @DisplayName("회사명 null 이면 companyName 위반이 발생한다")
  void nullCompanyName_violatesCompanyName() {
    SignupRequest request = new SignupRequest("user@chaeso.zip", "P@ssw0rd!", "채소러버", null,
        Occupation.DEVELOPMENT, true, false);

    assertThat(validator.validate(request))
        .anyMatch(v -> v.getPropertyPath().toString().equals("companyName"));
  }
}
