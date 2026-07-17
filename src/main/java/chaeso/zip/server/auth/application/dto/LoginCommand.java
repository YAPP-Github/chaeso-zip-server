package chaeso.zip.server.auth.application.dto;

/**
 * 로컬 로그인 유스케이스의 입력 커맨드. 표현 계층 로그인 요청 DTO 의 {@code toCommand()} 로 변환해 전달한다.
 */
public record LoginCommand(String email, String rawPassword) {

}
