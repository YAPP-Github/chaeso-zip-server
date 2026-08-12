package chaeso.zip.server.user.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Schema(description = "회원 탈퇴 결과")
public record WithdrawalResponse(
    @Schema(description = "탈퇴 시각(UTC)", requiredMode = Schema.RequiredMode.REQUIRED)
    Instant withdrawnAt
) {

  public static WithdrawalResponse from(LocalDateTime withdrawnAt) {
    return new WithdrawalResponse(withdrawnAt.toInstant(ZoneOffset.UTC));
  }
}
