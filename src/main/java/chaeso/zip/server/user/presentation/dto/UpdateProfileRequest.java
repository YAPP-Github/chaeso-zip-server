package chaeso.zip.server.user.presentation.dto;

import chaeso.zip.server.user.application.dto.UpdateProfileCommand;
import chaeso.zip.server.user.domain.Occupation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "내 정보 수정 요청. 회사/직무만 수정 가능하다")
public record UpdateProfileRequest(
    @Schema(description = "회사명", example = "채소집", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 50) String companyName,

    @Schema(description = "직무", example = "DEVELOPMENT", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull Occupation occupation) {

  public UpdateProfileCommand toCommand() {
    return new UpdateProfileCommand(companyName, occupation);
  }
}
