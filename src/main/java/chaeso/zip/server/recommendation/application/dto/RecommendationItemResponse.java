package chaeso.zip.server.recommendation.application.dto;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.estimation.domain.ClickCostPolicy;
import chaeso.zip.server.estimation.domain.vo.EstimationPricing;
import chaeso.zip.server.estimation.domain.vo.EstimationResult;
import chaeso.zip.server.recommendation.domain.MatchScore;
import chaeso.zip.server.recommendation.domain.PrimaryTarget;
import chaeso.zip.server.recommendation.domain.RecommendationReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "추천 채널")
public record RecommendationItemResponse(
    @Schema(description = "채널 식별자", example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID channelId,
    @Schema(description = "채널명", example = "11번가 광고", requiredMode = Schema.RequiredMode.REQUIRED)
    String channelName,
    @Schema(description = "적합도(%)", example = "78", requiredMode = Schema.RequiredMode.REQUIRED)
    int matchRate,
    @Schema(description = "추천 근거",
        example = "쇼핑·커머스 업종, 설정한 광고 목적에 적합하고 예산 내 집행이 가능해요",
        requiredMode = Schema.RequiredMode.REQUIRED)
    String recommendationReason,
    @Schema(description = "주요 타깃", example = "20~40대 여성",
        requiredMode = Schema.RequiredMode.REQUIRED)
    String primaryTarget,
    @Schema(description = "클릭당 비용(원)", example = "150", nullable = true)
    BigDecimal cpcWon,
    @Schema(description = "대표 단가의 과금 방식", example = "CPM", nullable = true)
    PricingModel pricingModel,
    @Schema(description = "최소 집행 예산(원)",
        example = "3000000", nullable = true)
    Long minBudgetWon,
    @Schema(description = "예상 노출 수 범위", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    CountRangeResponse estImpressions,
    @Schema(description = "예상 클릭 수 범위", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    CountRangeResponse estClicks,
    @Schema(description = "온보딩 예산(상한)으로 집행 가능한지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean isExecutable,
    @Schema(description = "집행에 부족한 금액(원)", example = "500000", nullable = true)
    Long shortfallWon) {

  /**
   * 단가 정보가 있는 상품이 없어 집행 금액을 알 수 없는 매체
   */
  public static RecommendationItemResponse quoteRequired(Channel channel, MatchScore score,
      Category industry) {
    return new RecommendationItemResponse(
        channel.getId(),
        channel.getName(),
        score.matchRate(),
        RecommendationReason.of(score, industry, null, false),
        PrimaryTarget.of(channel.getPrimaryAgeBand(), channel.getPrimaryGender()),
        null,
        null,
        null,
        null,
        null,
        false,
        null);
  }

  /**
   * 대표 상품으로 추정을 마친 매체
   */
  public static RecommendationItemResponse estimated(Channel channel, MatchScore score,
      Category industry, EstimationPricing pricing, EstimationResult result, long minBudgetWon,
      boolean isExecutable, Long shortfallWon, long estimationBudgetWon) {
    CountRangeResponse impressions = CountRangeResponse.from(result.impressions());
    CountRangeResponse clicks = CountRangeResponse.from(result.clicks());
    return new RecommendationItemResponse(
        channel.getId(),
        channel.getName(),
        score.matchRate(),
        RecommendationReason.of(score, industry, shortfallWon, true),
        PrimaryTarget.of(channel.getPrimaryAgeBand(), channel.getPrimaryGender()),
        ClickCostPolicy.cpcWon(pricing, estimationBudgetWon,
            clicks == null ? null : clicks.midpoint()),
        pricing.pricingModel(),
        minBudgetWon,
        impressions,
        clicks,
        isExecutable,
        shortfallWon);
  }
}
