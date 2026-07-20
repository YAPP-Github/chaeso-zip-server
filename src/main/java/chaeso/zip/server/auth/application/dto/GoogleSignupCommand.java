package chaeso.zip.server.auth.application.dto;

import chaeso.zip.server.user.domain.Occupation;

/** 구글 최종 회원가입 유스케이스의 입력 커맨드. 표현 계층 요청 DTO 의 {@code toCommand()} 로 변환해 전달한다. */
public record GoogleSignupCommand(
    String signupToken,
    String nickname,
    String companyName,
    Occupation occupation,
    boolean termsAgreed,
    boolean marketingAgreed) {
}
