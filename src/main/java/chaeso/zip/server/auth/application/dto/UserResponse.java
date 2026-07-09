package chaeso.zip.server.auth.application.dto;

import chaeso.zip.server.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 회원가입 성공 응답. 토큰은 로그인 API 소관이므로 반환하지 않는다. */
@Schema(description = "회원 응답")
public record UserResponse(
    @Schema(description = "회원 식별자", example = "0b8b8f2e-1c3a-4e5b-9a7d-2f1c6e8b4a90", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,
    @Schema(description = "이메일", example = "user@chaeso.zip", requiredMode = Schema.RequiredMode.REQUIRED)
    String email,
    @Schema(description = "닉네임", example = "채소러버", requiredMode = Schema.RequiredMode.REQUIRED)
    String nickname) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
  }
}
