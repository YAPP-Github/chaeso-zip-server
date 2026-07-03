package chaeso.zip.server.user.application.dto;

import chaeso.zip.server.user.domain.EmploymentStatus;
import chaeso.zip.server.user.domain.Occupation;

public record SignupCommand(
    String email,
    String rawPassword,
    String nickname,
    EmploymentStatus employmentStatus,
    String companyName,
    Occupation occupation,
    boolean termsAgreed,
    String termsVersion,
    boolean marketingAgreed) {
}
