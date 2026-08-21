package chaeso.zip.server.simulation.application.dto;

import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.estimation.domain.ClickCostPolicy;
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
    @Schema(description = "심볼 로고 이미지 URL", example = "https://assets.chaeso-zip.com/channels/550e8400-e29b-41d4-a716-446655440000/icon.png",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String iconUrl,
    @Schema(description = "추정 근거가 된 대표 상품 id. 단가 정보가 없으면 null",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    UUID channelProductId,
    @Schema(description = "배분 예산(원). 0 은 미집행", example = "1000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    long allocatedBudgetWon,
    @Schema(description = "전체 예산 대비 배분 비율(%)", example = "40",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    BigDecimal allocationPct,
    @Schema(description = "추정 노출 수 범위. 추정 불가 시 null", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    CountRangeResponse estImpressions,
    @Schema(description = "추정 클릭 수 범위. 추정 불가 시 null", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    CountRangeResponse estClicks,
    @Schema(description = """
        클릭당 비용(원). 클릭당 과금 매체는 단가 그대로, 그 외 매체는 \
        배분 예산 / 예상 클릭 수(중앙값)로 환산한다. 예상 클릭이 없으면 null""",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    BigDecimal cpcWon,
    @Schema(description = """
        1000회 노출당 단가(원). 대표 단가가 CPM 일 때만 채워진다. \
        화면에는 쓰지 않고 어떤 단가로 추정했는지 남기는 값""",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    BigDecimal cpmWon,
    @Schema(description = """
        집행 가능 판정의 기준이 된 최소 집행 금액(원). 대표 상품에 등록된 값이 있으면 그 값, \
        없으면 대표 단가다. 단가 정보가 없어 추정할 수 없는 매체만 null""",
        example = "1000000",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Long minBudgetWon,
    @Schema(description = "배분 예산으로 집행 가능한지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean isExecutable,
    @Schema(description = "집행에 부족한 금액(원). 집행 가능하면 null", example = "500000",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Long shortfallWon,
    @Schema(description = "산출 근거 고지", requiredMode = Schema.RequiredMode.REQUIRED)
    String basisNote) {

  /** 단가 정보가 있는 상품이 없어 추정할 수 없는 매체. */
  public static SimulationItemResponse quoteRequired(UUID channelId, String channelName, String iconUrl,
      long allocatedBudgetWon, BigDecimal allocationPct) {
    return new SimulationItemResponse(channelId, channelName, iconUrl, null, allocatedBudgetWon,
        allocationPct, null, null, null, null, null, false, null, BasisNote.quoteRequired());
  }

  /** 사용자가 예산을 배분하지 않은 매체. 집행에 필요한 금액만 알려준다. */
  public static SimulationItemResponse notAllocated(UUID channelId, String channelName, String iconUrl,
      UUID channelProductId, BigDecimal allocationPct, EstimationPricing pricing,
      long minBudgetWon, long shortfallWon) {
    return new SimulationItemResponse(channelId, channelName, iconUrl, channelProductId, 0L, allocationPct,
        null, null, cpcWon(pricing, 0L, null), cpmWon(pricing), minBudgetWon, false, shortfallWon,
        BasisNote.notAllocated());
  }

  public static SimulationItemResponse estimated(UUID channelId, String channelName, String iconUrl,
      UUID channelProductId, long allocatedBudgetWon, BigDecimal allocationPct,
      EstimationPricing pricing, EstimationResult result, long minBudgetWon, boolean executable,
      Long shortfallWon) {
    boolean hasImpressionData = result.impressions() != null;
    CountRangeResponse impressions =
        executable ? CountRangeResponse.from(result.impressions()) : null;
    CountRangeResponse clicks = executable ? CountRangeResponse.from(result.clicks()) : null;
    return new SimulationItemResponse(
        channelId,
        channelName,
        iconUrl,
        channelProductId,
        allocatedBudgetWon,
        allocationPct,
        impressions,
        clicks,
        cpcWon(pricing, allocatedBudgetWon, clicks),
        cpmWon(pricing),
        minBudgetWon,
        executable,
        shortfallWon,
        basisNoteFor(executable, hasImpressionData));
  }

  public static SimulationItemResponse from(BudgetSimulationItem item, String channelName, String iconUrl) {
    return new SimulationItemResponse(
        item.getChannelId(),
        channelName,
        iconUrl,
        item.getChannelProductId(),
        item.getAllocatedBudgetWon(),
        item.getAllocationPct(),
        CountRangeResponse.of(item.getEstImpressionsMin(), item.getEstImpressionsMax()),
        CountRangeResponse.of(item.getEstClicksMin(), item.getEstClicksMax()),
        item.getCpcWon(),
        item.getCpmWon(),
        item.getMinBudgetWon(),
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

  /**
   * 매체를 하나의 "클릭당 비용"으로 통일해 보여주기 위한 값.
   *
   * <p>예상 클릭이 없으면(추정 근거가 없거나 집행 불가) 환산할 수 없어 {@code null} 이다.
   */
  private static BigDecimal cpcWon(EstimationPricing pricing, long allocatedBudgetWon,
      CountRangeResponse clicks) {
    return ClickCostPolicy.cpcWon(pricing, allocatedBudgetWon,
        clicks == null ? null : clicks.midpoint());
  }

  private static BigDecimal cpmWon(EstimationPricing pricing) {
    return pricing.pricingModel() == PricingModel.CPM ? pricing.value() : null;
  }
}
