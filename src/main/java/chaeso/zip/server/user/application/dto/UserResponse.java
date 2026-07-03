package chaeso.zip.server.user.application.dto;

import chaeso.zip.server.user.domain.EmploymentStatus;
import chaeso.zip.server.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "회원 응답")
public record UserResponse(
    @Schema(
        description = "회원 식별자",
        example = "0b8b8f2e-1c3a-4e5b-9a7d-2f1c6e8b4a90",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,
    @Schema(description = "이메일", example = "user@chaeso.zip", requiredMode = Schema.RequiredMode.REQUIRED)
    String email,
    @Schema(description = "닉네임", example = "채소러버", requiredMode = Schema.RequiredMode.REQUIRED)
    String nickname,
    @Schema(description = "고용 상태", example = "EMPLOYEE", requiredMode = Schema.RequiredMode.REQUIRED)
    EmploymentStatus employmentStatus) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getEmploymentStatus());
  }
}
