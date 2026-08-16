package chaeso.zip.server.comparison.domain;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.estimation.domain.vo.ClickRange;
import chaeso.zip.server.estimation.domain.vo.ImpressionRange;
import chaeso.zip.server.recommendation.domain.MatchScore;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ChannelComparisonSnapshot(
    UUID channelId,
    String channelName,
    String previewImageUrl,
    List<String> displayPlatforms,
    String audienceSummary,
    List<String> adFormats,
    List<String> targetingMethods,
    Integer minBudgetWon,
    List<String> advantages,
    List<String> tags,
    String executionType,
    List<String> pricingModelsAll,
    BigDecimal cpcWon,
    BigDecimal cpmWon,
    Integer matchRate,
    int score,
    boolean executable,
    ImpressionRange impressions,
    ClickRange clicks) {

  /** 온보딩 없을 때의 카탈로그 스냅샷 */
  public static ChannelComparisonSnapshot catalogOnly(Channel channel, List<String> tags,
      BigDecimal cpcWon, BigDecimal cpmWon, List<String> pricingModelsAll) {
    return catalogOnly(channel, tags, cpcWon, cpmWon, pricingModelsAll, null, null);
  }

  /** 온보딩 없이 로그인만 한 경우의 카탈로그 스냅샷. 기본값 기준 예상 노출, 클릭 포함 */
  public static ChannelComparisonSnapshot catalogOnly(Channel channel, List<String> tags,
      BigDecimal cpcWon, BigDecimal cpmWon, List<String> pricingModelsAll,
      ImpressionRange impressions, ClickRange clicks) {
    return new ChannelComparisonSnapshot(
        channel.getId(),
        channel.getName(),
        channel.getPreviewImageUrl(),
        channel.getDisplayPlatforms(),
        channel.getAudienceSummary(),
        channel.getAdFormats(),
        channel.getTargetingMethods(),
        channel.getMinBudgetWon(),
        channel.getAdvantages(),
        tags,
        executionTypeName(channel),
        pricingModelsAll,
        cpcWon,
        cpmWon,
        null,
        0,
        false,
        impressions,
        clicks);
  }

  /** 온보딩 조건을 반영한 스냅샷. 적합도와 예상 노출, 클릭 포함 */
  public static ChannelComparisonSnapshot matched(Channel channel, MatchScore score,
      List<String> tags, BigDecimal cpcWon, BigDecimal cpmWon, List<String> pricingModelsAll,
      boolean executable, ImpressionRange impressions, ClickRange clicks) {
    return new ChannelComparisonSnapshot(
        channel.getId(),
        channel.getName(),
        channel.getPreviewImageUrl(),
        channel.getDisplayPlatforms(),
        channel.getAudienceSummary(),
        channel.getAdFormats(),
        channel.getTargetingMethods(),
        channel.getMinBudgetWon(),
        channel.getAdvantages(),
        tags,
        executionTypeName(channel),
        pricingModelsAll,
        cpcWon,
        cpmWon,
        score.matchRate(),
        score.score(),
        executable,
        impressions,
        clicks);
  }

  private static String executionTypeName(Channel channel) {
    return channel.getExecutionType() == null ? null : channel.getExecutionType().name();
  }
}
