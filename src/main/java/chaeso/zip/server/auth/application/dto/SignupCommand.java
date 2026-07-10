package chaeso.zip.server.auth.application.dto;

import chaeso.zip.server.user.domain.Occupation;

/** 회원가입 유스케이스 입력. rawPassword 는 인코딩 전 평문. */
public record SignupCommand(
    String email,
    String rawPassword,
    String nickname,
    String companyName,
    Occupation occupation,
    boolean termsAgreed,
    boolean marketingAgreed) {
}
