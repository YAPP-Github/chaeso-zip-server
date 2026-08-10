package chaeso.zip.server.simulation.presentation.dto;

import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.simulation.application.dto.SimulationCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Schema(description = "예산 시뮬레이션 요청")
public record SimulationRequest(
    @Schema(description = "총 예산(원). 10만 이상 1,000만 이하", example = "3000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Min(100_000) @Max(10_000_000) Integer totalBudgetWon,

    @Schema(description = """
        집행 기간(온보딩과 같은 구간). 구간이라 계산에는 대표 일수를 쓴다 — \
        LE_1W=1주 이하(7일), W2_3=2-3주(17일), M1=1개월(30일), M2_3=2-3개월(75일), \
        GE_3M=3개월 이상(90일). 하루 예산을 계산할 때 프론트도 같은 일수를 써야 한다""",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull CampaignPeriod period,

    @Schema(description = "매체별 예산 배분. 1개 이상", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty @Valid List<AllocationRequest> allocations) {

  @JsonIgnore
  @AssertTrue(message = "같은 채널에 예산을 두 번 배분할 수 없습니다")
  public boolean isAllocatedToDistinctChannels() {
    if (allocations == null) {
      return true;
    }
    List<UUID> channelIds = allocations.stream()
        .map(AllocationRequest::channelId)
        .filter(Objects::nonNull)
        .toList();
    return channelIds.size() == Set.copyOf(channelIds).size();
  }

  @JsonIgnore
  @AssertTrue(message = "배분한 예산의 합이 총 예산을 넘을 수 없습니다")
  public boolean isWithinTotalBudget() {
    if (allocations == null || totalBudgetWon == null) {
      return true;   // @NotNull / @NotEmpty 가 따로 보고한다
    }
    long allocated = allocations.stream()
        .map(AllocationRequest::budgetWon)
        .filter(Objects::nonNull)
        .mapToLong(Integer::longValue)
        .sum();
    return allocated <= totalBudgetWon;
  }

  public SimulationCommand toCommand() {
    return new SimulationCommand(totalBudgetWon, period,
        allocations.stream().map(AllocationRequest::toCommand).toList());
  }
}
