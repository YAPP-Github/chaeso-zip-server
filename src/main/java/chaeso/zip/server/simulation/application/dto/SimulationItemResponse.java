package chaeso.zip.server.simulation.application.dto;

import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.domain.vo.EstimationPricing;
import chaeso.zip.server.estimation.domain.vo.EstimationResult;
import chaeso.zip.server.simulation.domain.BasisNote;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulationItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "매체별 시뮬레이션 결과")
public record SimulationItemResponse(
    @Schema(description = "채널 id", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID channelId,
    @Schema(description = "채널명", example = "11번가 광고", requiredMode = Schema.RequiredMode.REQUIRED)
    String channelName,
    @Schema(description = "추정 근거가 된 대표 상품 id. 단가 정보가 없으면 null", nullable = true)
    UUID channelProductId,
    @Schema(description = "배분 예산(원). 0 은 미집행", example = "1000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    long allocatedBudgetWon,
    @Schema(description = "전체 예산 대비 배분 비율(%)", example = "40", nullable = true)
    BigDecimal allocationPct,
    @Schema(description = "추정 노출 수 범위. 추정 불가 시 null", nullable = true)
    CountRangeResponse estImpressions,
    @Schema(description = "추정 클릭 수 범위. 추정 불가 시 null", nullable = true)
    CountRangeResponse estClicks,
    @Schema(description = "클릭당 단가(원). 대표 단가가 CPC 일 때만 채워진다", nullable = true)
    BigDecimal cpcWon,
    @Schema(description = "1000회 노출당 단가(원). 대표 단가가 CPM 일 때만 채워진다", nullable = true)
    BigDecimal cpmWon,
    @Schema(description = "배분 예산으로 집행 가능한지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean isExecutable,
    @Schema(description = "집행에 부족한 금액(원). 집행 가능하면 null", example = "500000",
        nullable = true)
    Long shortfallWon,
    @Schema(description = "산출 근거 고지", requiredMode = Schema.RequiredMode.REQUIRED)
    String basisNote) {

  /** 단가 정보가 있는 상품이 없어 추정할 수 없는 매체. */
  public static SimulationItemResponse quoteRequired(UUID channelId, String channelName,
      long allocatedBudgetWon, BigDecimal allocationPct) {
    return new SimulationItemResponse(channelId, channelName, null, allocatedBudgetWon,
        allocationPct, null, null, null, null, false, null, BasisNote.quoteRequired());
  }

  /** 사용자가 예산을 배분하지 않은 매체. 집행에 필요한 금액만 알려준다. */
  public static SimulationItemResponse notAllocated(UUID channelId, String channelName,
      UUID channelProductId, BigDecimal allocationPct, EstimationPricing pricing,
      Long shortfallWon) {
    return new SimulationItemResponse(channelId, channelName, channelProductId, 0L, allocationPct,
        null, null, cpcWon(pricing), cpmWon(pricing), false, shortfallWon,
        BasisNote.notAllocated());
  }

  /** 추정을 마친 매체. 집행 불가면 노출·클릭 대신 부족 금액만 의미를 가진다. */
  public static SimulationItemResponse estimated(UUID channelId, String channelName,
      UUID channelProductId, long allocatedBudgetWon, BigDecimal allocationPct,
      EstimationPricing pricing, EstimationResult result, Long shortfallWon) {
    boolean executable = result.isExecutable();
    boolean hasImpressionData = result.impressions() != null;
    return new SimulationItemResponse(
        channelId,
        channelName,
        channelProductId,
        allocatedBudgetWon,
        allocationPct,
        executable ? CountRangeResponse.from(result.impressions()) : null,
        executable ? CountRangeResponse.from(result.clicks()) : null,
        cpcWon(pricing),
        cpmWon(pricing),
        executable,
        shortfallWon,
        basisNoteFor(executable, hasImpressionData));
  }

  /** 저장된 스냅샷을 그대로 되살린다. 재계산하지 않는다. */
  public static SimulationItemResponse from(BudgetSimulationItem item, String channelName) {
    return new SimulationItemResponse(
        item.getChannelId(),
        channelName,
        item.getChannelProductId(),
        item.getAllocatedBudgetWon(),
        item.getAllocationPct(),
        CountRangeResponse.of(item.getEstImpressionsMin(), item.getEstImpressionsMax()),
        CountRangeResponse.of(item.getEstClicksMin(), item.getEstClicksMax()),
        item.getCpcWon(),
        item.getCpmWon(),
        item.isExecutable(),
        item.getShortfallWon(),
        item.getBasisNote());
  }

  private static String basisNoteFor(boolean executable, boolean hasImpressionData) {
    if (!executable) {
      return BasisNote.budgetShortfall();
    }
    return hasImpressionData ? BasisNote.common() : BasisNote.noImpressionData();
  }

  private static BigDecimal cpcWon(EstimationPricing pricing) {
    return pricing.pricingModel() == PricingModel.CPC ? pricing.value() : null;
  }

  private static BigDecimal cpmWon(EstimationPricing pricing) {
    return pricing.pricingModel() == PricingModel.CPM ? pricing.value() : null;
  }
}
