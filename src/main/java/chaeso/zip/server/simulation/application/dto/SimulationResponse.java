package chaeso.zip.server.simulation.application.dto;

import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulation;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "예산 시뮬레이션 결과")
@JsonInclude(Include.NON_NULL)
public record SimulationResponse(
    @Schema(description = "저장된 시뮬레이션 id. 저장 전 계산 결과에는 생략된다",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    UUID simulationId,
    @Schema(description = "총 예산(원)", example = "3000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    long totalBudgetWon,
    @Schema(description = "집행 기간(온보딩과 같은 구간)", example = "M1",
        requiredMode = Schema.RequiredMode.REQUIRED)
    CampaignPeriod period,
    @Schema(description = "집행 가능한 매체들의 추정 노출 수 합(범위 중앙값 기준)", example = "1200000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    long totalEstImpressions,
    @Schema(description = "집행 가능한 매체들의 추정 클릭 수 합(범위 중앙값 기준)", example = "24000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    long totalEstClicks,
    @Schema(description = "집행 가능한 매체 개수", example = "2",
        requiredMode = Schema.RequiredMode.REQUIRED)
    int executableChannelCount,
    @Schema(description = "매체별 결과. 요청한 순서를 유지한다",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<SimulationItemResponse> items) {

  /** 저장 전, 계산만 마친 결과. */
  public static SimulationResponse of(long totalBudgetWon, CampaignPeriod period,
      long totalEstImpressions, long totalEstClicks, List<SimulationItemResponse> items) {
    return new SimulationResponse(null, totalBudgetWon, period, totalEstImpressions,
        totalEstClicks, executableChannelCount(items), items);
  }

  /** 저장된 스냅샷을 그대로 되살린다. 재계산하지 않는다. */
  public static SimulationResponse from(BudgetSimulation simulation,
      List<SimulationItemResponse> items) {
    return new SimulationResponse(
        simulation.getId(),
        simulation.getTotalBudgetWon(),
        simulation.getPeriod(),
        simulation.getTotalEstImpressions(),
        simulation.getTotalEstClicks(),
        executableChannelCount(items),
        items);
  }

  /** 저장이 끝난 뒤 발급된 id 를 붙인다. */
  public SimulationResponse withSimulationId(UUID simulationId) {
    return new SimulationResponse(simulationId, totalBudgetWon, period, totalEstImpressions,
        totalEstClicks, executableChannelCount, items);
  }

  private static int executableChannelCount(List<SimulationItemResponse> items) {
    return (int) items.stream().filter(SimulationItemResponse::isExecutable).count();
  }
}
