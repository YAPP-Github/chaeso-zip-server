package chaeso.zip.server.recommendation.application.dto;

import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.recommendation.domain.RecommendationSnapshot;
import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendation;
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
    @Schema(description = "클릭당 비용(원). 예상 클릭이 없어 환산할 수 없으면 null", example = "150",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    BigDecimal cpcWon,
    @Schema(description = "대표 단가의 과금 방식. 등록된 단가가 없으면 null", example = "CPM",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    PricingModel pricingModel,
    @Schema(description = "최소 집행 예산(원). 등록된 단가가 없으면 null", example = "3000000",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Long minBudgetWon,
    @Schema(description = "예상 노출 수 범위. 추정 불가 시 null",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    CountRangeResponse estImpressions,
    @Schema(description = "예상 클릭 수 범위. 추정 불가 시 null",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    CountRangeResponse estClicks,
    @Schema(description = "온보딩 예산(상한)으로 집행 가능한지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean isExecutable,
    @Schema(description = "집행에 부족한 금액(원). 집행 가능하면 null", example = "500000",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Long shortfallWon) {

  /**
   * 계산 결과에서 화면에 쓰는 값만 골라 담는다. 근거 태그·대표 단가·과금 방식 전체는 저장에만 쓰므로
   * 응답에 넣지 않는다.
   */
  public static RecommendationItemResponse from(RecommendationSnapshot snapshot) {
    return new RecommendationItemResponse(
        snapshot.channelId(),
        snapshot.channelName(),
        snapshot.matchRate(),
        snapshot.reason(),
        snapshot.primaryTarget(),
        snapshot.cpcWon(),
        snapshot.pricingModel(),
        snapshot.minBudgetWon(),
        CountRangeResponse.from(snapshot.impressions()),
        CountRangeResponse.from(snapshot.clicks()),
        snapshot.isExecutable(),
        snapshot.shortfallWon());
  }

  /**
   * 저장된 추천 한 행을 그대로 되살린다. 적합도·근거·금액·추정값은 모두 저장 시점 스냅샷이며
   * 재계산하지 않는다.
   *
   * @param channelName 지금의 채널명
   */
  public static RecommendationItemResponse from(ChannelRecommendation item, String channelName) {
    return new RecommendationItemResponse(
        item.getChannelId(),
        channelName,
        item.getScore(),
        item.getReason(),
        item.getAudienceSummarySnap(),
        item.getCpcWon(),
        item.getEstPricingModel(),
        item.getMinBudgetWonSnap(),
        CountRangeResponse.of(item.getEstImpressionsMin(), item.getEstImpressionsMax()),
        CountRangeResponse.of(item.getEstClicksMin(), item.getEstClicksMax()),
        item.isExecutable(),
        item.getShortfallWon());
  }
}
