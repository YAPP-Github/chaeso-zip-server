package chaeso.zip.server.user.application.dto;

import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 정보")
public record UserProfileResponse(
    @Schema(description = "닉네임", example = "채소러버", requiredMode = Schema.RequiredMode.REQUIRED)
    String nickname,

    @Schema(description = "계정 이름(이메일)", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    String email,

    @Schema(description = "회사명", example = "채소집", requiredMode = Schema.RequiredMode.REQUIRED)
    String companyName,

    @Schema(description = "직무", requiredMode = Schema.RequiredMode.REQUIRED)
    Occupation occupation) {

  public static UserProfileResponse from(User user) {
    return new UserProfileResponse(
        user.getNickname(), user.getEmail(), user.getCompanyName(), user.getOccupation());
  }
}
