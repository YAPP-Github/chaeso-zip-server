package chaeso.zip.server.simulation.application.dto;

import chaeso.zip.server.simulation.domain.entity.BudgetSimulation;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulationItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Schema(description = "저장된 시뮬레이션 목록 요약. 예산·추정치와 매체별 상세는 상세 조회에서 받는다")
public record SimulationSummaryResponse(
    @Schema(description = "저장된 시뮬레이션 id", example = "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,
    @Schema(description = "저장 요청시 입력받은 서비스명", example = "채소집",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String serviceName,
    @Schema(description = "저장 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDateTime createdAt,
    @Schema(description = "예산을 배분한 매체명 리스트. 저장 순서이며 null 이 아닌 배열",
        example = "[\"11번가 광고\", \"당근마켓 광고\"]",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> channelNames) {

  /**
   * 스냅샷에는 사용자가 담아만 두고 예산을 주지 않은 매체(배분 0원)도 상세 화면을 그대로 재현하려고
   * 남아 있다. 목록의 매체명은 실제로 예산을 배분한 매체만 저장 순서대로 담는다.
   */
  public static SimulationSummaryResponse from(BudgetSimulation simulation,
      List<BudgetSimulationItem> items, Map<UUID, String> channelNames) {
    return new SimulationSummaryResponse(
        simulation.getId(),
        simulation.getServiceName(),
        simulation.getCreatedAt(),
        items.stream()
            .filter(item -> item.getAllocatedBudgetWon() > 0)
            .map(item -> channelNames.get(item.getChannelId()))
            .filter(Objects::nonNull)
            .toList());
  }
}
