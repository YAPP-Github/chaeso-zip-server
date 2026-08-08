package chaeso.zip.server.recommendation.domain;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 온보딩 응답과 채널을 코드값으로 맞춰 적합도를 계산한다.
 */
public final class ChannelMatcher {

  private ChannelMatcher() {
  }

  /**
   * 채널 하나의 적합도
   */
  public static MatchScore match(Onboarding onboarding, Channel channel,
      List<ChannelProduct> products) {
    Objects.requireNonNull(onboarding, "onboarding 은 null 일 수 없습니다");
    Objects.requireNonNull(channel, "channel 은 null 일 수 없습니다");

    Set<MatchAxis> applied = appliedAxes(onboarding);

    Set<MatchAxis> matched = EnumSet.noneOf(MatchAxis.class);
    if (suitsIndustry(onboarding.getIndustry(), channel.getSuitableCategories())) {
      matched.add(MatchAxis.CATEGORY);
    }
    if (supportsObjective(onboarding.getCampaignObjective(), products)) {
      matched.add(MatchAxis.OBJECTIVE);
    }
    if (applied.contains(MatchAxis.AGE_BAND)
        && overlapsAgeBand(onboarding.getTargetAgeBands(), channel.getAgeBandCodes())) {
      matched.add(MatchAxis.AGE_BAND);
    }
    return new MatchScore(matched, applied);
  }

  private static Set<MatchAxis> appliedAxes(Onboarding onboarding) {
    Set<MatchAxis> applied = EnumSet.allOf(MatchAxis.class);
    if (ageUndecided(onboarding.getTargetAgeBands())) {
      applied.remove(MatchAxis.AGE_BAND);
    }
    return applied;
  }

  private static boolean ageUndecided(Collection<AgeBand> targetAgeBands) {
    return targetAgeBands != null && targetAgeBands.contains(AgeBand.UNDECIDED);
  }

  private static boolean suitsIndustry(Category industry, List<Category> suitableCategories) {
    return industry != null && suitableCategories != null && suitableCategories.contains(industry);
  }

  private static boolean supportsObjective(CampaignObjective objective,
      List<ChannelProduct> products) {
    if (objective == null || products == null) {
      return false;
    }
    return products.stream()
        .map(ChannelProduct::getSupportedObjectives)
        .filter(Objects::nonNull)
        .anyMatch(supported -> supported.contains(objective));
  }

  private static boolean overlapsAgeBand(Collection<AgeBand> targetAgeBands,
      Collection<AgeBand> channelAgeBands) {
    if (targetAgeBands == null || channelAgeBands == null) {
      return false;
    }
    return targetAgeBands.stream().anyMatch(channelAgeBands::contains);
  }
}
