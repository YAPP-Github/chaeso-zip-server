package chaeso.zip.server.simulation.application.dto;

import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulation;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulationItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Schema(description = "저장된 시뮬레이션 목록 요약. 매체별 상세는 상세 조회에서 받는다")
public record SimulationSummaryResponse(
    @Schema(description = "저장된 시뮬레이션 id", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID simulationId,
    @Schema(description = "저장 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDateTime createdAt,
    @Schema(description = "총 예산(원)", example = "3000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    long totalBudgetWon,
    @Schema(description = "집행 기간(온보딩과 같은 구간)", example = "M1",
        requiredMode = Schema.RequiredMode.REQUIRED)
    CampaignPeriod period,
    @Schema(description = "저장 당시 추정 노출 수 합(범위 중앙값 기준)", example = "1150000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    long totalEstImpressions,
    @Schema(description = "저장 당시 추정 클릭 수 합(범위 중앙값 기준)", example = "23000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    long totalEstClicks,
    @Schema(description = "예산을 배분한 매체 개수", example = "3",
        requiredMode = Schema.RequiredMode.REQUIRED)
    int channelCount,
    @Schema(description = "집행 가능한 매체 개수", example = "2",
        requiredMode = Schema.RequiredMode.REQUIRED)
    int executableChannelCount,
    @Schema(description = "어떤 조합이었는지 알아볼 수 있게 예산을 배분한 매체명만 최대 3개 보여 준다. null 이 아닌 배열",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> channelNames) {

  /** 목록에서 조합을 알아볼 정도로만 보여 주는 대표 매체 수 */
  private static final int PREVIEW_CHANNEL_NAMES = 3;

  /**
   * 스냅샷에는 사용자가 담아만 두고 예산을 주지 않은 매체(배분 0원)도 상세 화면을 그대로 재현하려고
   * 남아 있다. 목록의 매체 수와 대표 매체명은 실제로 예산을 배분한 매체만 센다.
   */
  public static SimulationSummaryResponse from(BudgetSimulation simulation,
      List<BudgetSimulationItem> items, Map<UUID, String> channelNames) {
    List<BudgetSimulationItem> allocated = items.stream()
        .filter(item -> item.getAllocatedBudgetWon() > 0)
        .toList();
    return new SimulationSummaryResponse(
        simulation.getId(),
        simulation.getCreatedAt(),
        simulation.getTotalBudgetWon(),
        simulation.getPeriod(),
        simulation.getTotalEstImpressions(),
        simulation.getTotalEstClicks(),
        allocated.size(),
        (int) allocated.stream().filter(BudgetSimulationItem::isExecutable).count(),
        allocated.stream()
            .map(item -> channelNames.get(item.getChannelId()))
            .filter(Objects::nonNull)
            .limit(PREVIEW_CHANNEL_NAMES)
            .toList());
  }
}
