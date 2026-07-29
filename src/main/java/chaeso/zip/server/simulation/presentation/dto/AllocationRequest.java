package chaeso.zip.server.simulation.presentation.dto;

import chaeso.zip.server.simulation.application.dto.AllocationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "매체별 예산 배분")
public record AllocationRequest(
    @Schema(description = "배분 대상 채널 id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull UUID channelId,

    @Schema(description = "배분 예산(원). 0 은 미집행", example = "1000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @PositiveOrZero Integer budgetWon,

    @Schema(description = "전체 예산 대비 배분 비율(%)", example = "40",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal allocationPct) {

  public AllocationCommand toCommand() {
    return new AllocationCommand(channelId, budgetWon, allocationPct);
  }
}
