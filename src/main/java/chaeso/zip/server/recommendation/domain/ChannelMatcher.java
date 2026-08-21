package chaeso.zip.server.recommendation.domain;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 온보딩 응답과 채널을 코드값으로 맞춰 적합도를 계산한다.
 *
 * <p>축마다 얼마나 맞았는지를 0.0~1.0 으로 매긴다. 적합 업종을 넓게 잡은 매체,
 * 전 연령을 덮는 매체, 상품 하나만 목적을 지원하는 매체가 정확히 맞는 매체와 같은
 * 점수를 받지 않게 하기 위해서다.
 *
 * <p>예산 축은 대표 단가를 읽어야 알 수 있어 여기서 채점하지 않는다. 호출자가
 * {@link MatchScore#with(MatchAxis, double)} 로 {@link BudgetFit} 결과를 더한다.
 */
public final class ChannelMatcher {

  /** 적합 업종에 들어 있기만 할 때의 기본 적합 정도 */
  private static final double SUITABLE_BASE = 0.5;

  /** 적합 업종을 좁게 잡은 매체에 더 주는 특화 가산의 폭 */
  private static final double SUITABLE_FOCUS_BONUS = 0.3;

  /** 상품이 목적을 하나라도 지원할 때의 기본 적합 정도 */
  private static final double OBJECTIVE_BASE = 0.55;

  /** 인접 목적만 지원할 때의 적합 정도 */
  private static final double OBJECTIVE_ADJACENT = 0.25;

  private static final double FULL = 1.0;
  private static final double NONE = 0.0;

  private ChannelMatcher() {
  }

  /**
   * 채널 하나의 캠페인 조건 적합도 (예산 축 제외)
   */
  public static MatchScore match(Onboarding onboarding, Channel channel,
      List<ChannelProduct> products) {
    Objects.requireNonNull(onboarding, "onboarding 은 null 일 수 없습니다");
    Objects.requireNonNull(channel, "channel 은 null 일 수 없습니다");

    Map<MatchAxis, Double> fits = new EnumMap<>(MatchAxis.class);
    Set<MatchAxis> unknown = EnumSet.noneOf(MatchAxis.class);

    scoreCategory(onboarding, channel, fits, unknown);
    scoreObjective(onboarding, products, fits, unknown);
    scoreAgeBand(onboarding, channel, fits, unknown);

    return new MatchScore(fits, unknown);
  }

  /**
   * 업종 축
   */
  private static void scoreCategory(Onboarding onboarding, Channel channel,
      Map<MatchAxis, Double> fits, Set<MatchAxis> unknown) {
    Category industry = onboarding.getIndustry();
    if (industry == null) {
      return;   // 온보딩이 기준을 주지 않은 축은 감점 없이 뺀다
    }

    List<Category> suitable = channel.getSuitableCategories();
    boolean hasBasis = channel.getPrimaryCategory() != null
        || (suitable != null && !suitable.isEmpty());
    if (!hasBasis) {
      unknown.add(MatchAxis.CATEGORY);
      return;
    }

    if (industry == channel.getPrimaryCategory()) {
      fits.put(MatchAxis.CATEGORY, FULL);
      return;
    }
    if (suitable == null || !suitable.contains(industry)) {
      fits.put(MatchAxis.CATEGORY, NONE);
      return;
    }
    double focus = FULL / suitable.size();
    fits.put(MatchAxis.CATEGORY, SUITABLE_BASE + SUITABLE_FOCUS_BONUS * focus);
  }

  /**
   * 광고 목적 축. 목적을 지원하는 상품의 비율로 채점한다.
   */
  private static void scoreObjective(Onboarding onboarding, List<ChannelProduct> products,
      Map<MatchAxis, Double> fits, Set<MatchAxis> unknown) {
    CampaignObjective objective = onboarding.getCampaignObjective();
    if (objective == null) {
      return;
    }

    List<List<CampaignObjective>> declared = declaredObjectives(products);
    if (declared.isEmpty()) {
      unknown.add(MatchAxis.OBJECTIVE);
      return;
    }

    long supporting = declared.stream()
        .filter(supported -> supported.contains(objective))
        .count();
    if (supporting > 0) {
      double supportRatio = (double) supporting / declared.size();
      fits.put(MatchAxis.OBJECTIVE,
          OBJECTIVE_BASE + (FULL - OBJECTIVE_BASE) * supportRatio);
      return;
    }

    boolean adjacent = declared.stream()
        .flatMap(List::stream)
        .anyMatch(supported -> ObjectiveAffinity.isAdjacent(objective, supported));
    fits.put(MatchAxis.OBJECTIVE, adjacent ? OBJECTIVE_ADJACENT : NONE);
  }

  /**
   * 연령 축
   */
  private static void scoreAgeBand(Onboarding onboarding, Channel channel,
      Map<MatchAxis, Double> fits, Set<MatchAxis> unknown) {
    Set<AgeBand> target = decidedAgeBands(onboarding.getTargetAgeBands());
    if (target.isEmpty()) {
      return;   // '잘 모르겠어요' 등 기준이 없는 경우
    }

    Collection<AgeBand> channelBands = channel.getAgeBandCodes();
    if (channelBands == null || channelBands.isEmpty()) {
      unknown.add(MatchAxis.AGE_BAND);
      return;
    }

    Set<AgeBand> reached = decidedAgeBands(channelBands);
    if (reached.isEmpty()) {
      unknown.add(MatchAxis.AGE_BAND);
      return;
    }

    long overlap = target.stream().filter(reached::contains).count();
    if (overlap == 0) {
      fits.put(MatchAxis.AGE_BAND, NONE);
      return;
    }

    double coverage = (double) overlap / target.size();
    double precision = (double) overlap / reached.size();
    fits.put(MatchAxis.AGE_BAND, harmonicMean(coverage, precision));
  }

  /** 목적이 명시된 상품만 남긴다. 명시가 없는 상품은 지원 비율의 분모에서도 뺀다 */
  private static List<List<CampaignObjective>> declaredObjectives(
      List<ChannelProduct> products) {
    if (products == null) {
      return List.of();
    }
    return products.stream()
        .map(ChannelProduct::getSupportedObjectives)
        .filter(supported -> supported != null && !supported.isEmpty())
        .toList();
  }

  private static Set<AgeBand> decidedAgeBands(Collection<AgeBand> ageBands) {
    if (ageBands == null || ageBands.isEmpty()) {
      return Set.of();
    }
    Set<AgeBand> decided = EnumSet.noneOf(AgeBand.class);
    for (AgeBand ageBand : ageBands) {
      if (ageBand != null && ageBand != AgeBand.UNDECIDED) {
        decided.add(ageBand);
      }
    }
    return decided;
  }

  private static double harmonicMean(double one, double other) {
    return 2 * one * other / (one + other);
  }
}
