package chaeso.zip.server.recommendation.domain;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.domain.ClickCostPolicy;
import chaeso.zip.server.estimation.domain.vo.ClickRange;
import chaeso.zip.server.estimation.domain.vo.EstimationPricing;
import chaeso.zip.server.estimation.domain.vo.EstimationResult;
import chaeso.zip.server.estimation.domain.vo.ImpressionRange;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record RecommendationSnapshot(
    UUID channelId,
    String channelName,
    String wordmarkUrl,
    int matchRate,
    Set<MatchAxis> matchedAxes,
    String reason,
    String primaryTarget,
    BigDecimal cpcWon,
    PricingModel pricingModel,
    BigDecimal unitPrice,
    List<PricingModel> pricingModels,
    ImpressionRange impressions,
    ClickRange clicks,
    Long minBudgetWon,
    boolean isExecutable,
    Long shortfallWon) {

  /** 대표 단가가 없어 집행 금액을 알 수 없는 매체 */
  public static RecommendationSnapshot quoteRequired(Channel channel, MatchScore score,
      Category industry, List<PricingModel> pricingModels) {
    return new RecommendationSnapshot(
        channel.getId(),
        channel.getName(),
        channel.getWordmarkUrl(),
        score.matchRate(),
        score.matchedAxes(),
        RecommendationReason.of(score, industry, null, false),
        PrimaryTarget.of(channel.getPrimaryAgeBand(), channel.getPrimaryGender()),
        null,
        null,
        null,
        pricingModels,
        null,
        null,
        null,
        false,
        null);
  }

  public static RecommendationSnapshot estimated(Channel channel, MatchScore score,
      Category industry, EstimationPricing pricing, List<PricingModel> pricingModels,
      EstimationResult result, long minBudgetWon, boolean isExecutable, Long shortfallWon,
      long estimationBudgetWon) {
    ClickRange clicks = result.clicks();
    return new RecommendationSnapshot(
        channel.getId(),
        channel.getName(),
        channel.getWordmarkUrl(),
        score.matchRate(),
        score.matchedAxes(),
        RecommendationReason.of(score, industry, shortfallWon, true),
        PrimaryTarget.of(channel.getPrimaryAgeBand(), channel.getPrimaryGender()),
        ClickCostPolicy.cpcWon(pricing, estimationBudgetWon, midpoint(clicks)),
        pricing.pricingModel(),
        pricing.value(),
        pricingModels,
        result.impressions(),
        clicks,
        minBudgetWon,
        isExecutable,
        shortfallWon);
  }

  public List<String> reasonTags() {
    return matchedAxes.stream().filter(MatchAxis::isSubject).map(MatchAxis::name).toList();
  }

  public List<String> pricingModelNames() {
    return pricingModels.stream().map(PricingModel::name).toList();
  }

  private static Long midpoint(ClickRange clicks) {
    return clicks == null ? null : clicks.midpoint();
  }
}
