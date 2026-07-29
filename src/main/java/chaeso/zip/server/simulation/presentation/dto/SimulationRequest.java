package chaeso.zip.server.simulation.presentation.dto;

import chaeso.zip.server.simulation.application.dto.SimulationCommand;
import chaeso.zip.server.simulation.domain.vo.SimPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "예산 시뮬레이션 요청")
public record SimulationRequest(
    @Schema(description = "총 예산(원). 10만 이상 500만 이하", example = "3000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Min(100_000) @Max(5_000_000) Integer totalBudgetWon,

    @Schema(description = "집행 기간. W1=1주, W2=2주, M1=1개월, M3=3개월",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull SimPeriod period,

    @Schema(description = "매체별 예산 배분. 1개 이상", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty @Valid List<AllocationRequest> allocations) {

  public SimulationCommand toCommand() {
    return new SimulationCommand(totalBudgetWon, period,
        allocations.stream().map(AllocationRequest::toCommand).toList());
  }
}
