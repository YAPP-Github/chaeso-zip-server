package chaeso.zip.server.comparison.application;

import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonItemResponse;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonResponse;
import chaeso.zip.server.estimation.application.DefaultCtrProvider;
import chaeso.zip.server.estimation.domain.ClickCostPolicy;
import chaeso.zip.server.estimation.domain.EstimationService;
import chaeso.zip.server.estimation.domain.RepresentativeProduct;
import chaeso.zip.server.estimation.domain.vo.ClickRange;
import chaeso.zip.server.estimation.domain.vo.EstimationPricing;
import chaeso.zip.server.estimation.domain.vo.EstimationResult;
import chaeso.zip.server.estimation.domain.vo.ImpressionRange;
import chaeso.zip.server.estimation.domain.vo.PeriodDaysPolicy;
import chaeso.zip.server.onboarding.domain.OnboardingNotFoundException;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.recommendation.domain.ChannelMatcher;
import chaeso.zip.server.recommendation.domain.MatchAxis;
import chaeso.zip.server.recommendation.domain.MatchScore;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선택한 채널의 카탈로그 정보와 온보딩 맞춤 지표를 계산한다.
 *
 * <p>비로그인은 카탈로그 상세와 적합도, 예상 노출·클릭을 노출하지 않는다
 * 로그인한 뒤 온보딩이 있으면 고른 채널만 적합도순 정렬
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelComparisonServiceImpl implements ChannelComparisonService {

  private static final int INSIGHT_TAG_LIMIT = 2;

  /** 추천과 같은 비교 순서. 고른 채널에만 적용한다. */
  private static final Comparator<ScoredItem> BEST_FIRST = Comparator
      .comparingInt(ScoredItem::score).reversed()
      .thenComparing(ScoredItem::executable, Comparator.reverseOrder())
      .thenComparing(item -> item.item().channelName());

  private final ChannelRepository channelRepository;
  private final ChannelProductRepository channelProductRepository;
  private final ChannelPricingRepository channelPricingRepository;
  private final OnboardingRepository onboardingRepository;
  private final DefaultCtrProvider defaultCtrProvider;

  /**
   * 선택된 채널 ID 목록을 받아 정적 또는 온보딩 맞춤 비교 응답을 생성한다.
   *
   * @param channelIds   비교할 채널 식별자 목록 (2~3개)
   * @param onboardingId 온보딩 식별자 (선택)
   * @param requesterId  요청자 회원 식별자 (선택)
   * @return 채널 비교 응답 DTO
   */
  @Override
  public ChannelComparisonResponse compare(List<UUID> channelIds, UUID onboardingId,
      UUID requesterId) {
    List<Channel> channels = channelIds.stream()
        .map(id -> channelRepository.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new ChannelNotFoundException(id)))
        .toList();

    Map<UUID, List<ChannelProduct>> productsByChannel = productsByChannel(channels);
    Map<UUID, List<ChannelPricing>> pricingsByProduct = pricingsByProduct(productsByChannel);
    BigDecimal defaultCtrPercent = defaultCtrProvider.averageCtrPercent();
    boolean loggedIn = requesterId != null;

    if (onboardingId == null) {
      return ChannelComparisonResponse.of(channels.stream()
          .map(channel -> staticItem(channel,
              productsByChannel.getOrDefault(channel.getId(), List.of()), pricingsByProduct,
              defaultCtrPercent))
          .map(item -> loggedIn ? item : item.hideCatalogDetails())
          .toList());
    }

    Onboarding onboarding = onboardingRepository.findById(onboardingId)
        .orElseThrow(() -> new OnboardingNotFoundException(onboardingId));
    if (onboarding.getUserId() != null && !onboarding.getUserId().equals(requesterId)) {
      throw new OnboardingNotFoundException(onboardingId);
    }
    int periodDays = PeriodDaysPolicy.daysOf(onboarding.getPeriod());
    long budgetWon = onboarding.getBudgetMax();

    List<ScoredItem> scored = channels.stream()
        .map(channel -> personalizedItem(onboarding, channel,
            productsByChannel.getOrDefault(channel.getId(), List.of()), pricingsByProduct,
            budgetWon, periodDays, defaultCtrPercent))
        .toList();
    if (loggedIn) {
      return ChannelComparisonResponse.of(scored.stream()
          .sorted(BEST_FIRST)
          .map(ScoredItem::item)
          .toList());
    }
    return ChannelComparisonResponse.of(scored.stream()
        .map(candidate -> candidate.item().hideCatalogDetails())
        .toList());
  }

  /**
   * 온보딩이 없을 때 보여 줄 채널 카탈로그 비교 항목을 만든다.
   */
  private ChannelComparisonItemResponse staticItem(Channel channel, List<ChannelProduct> products,
      Map<UUID, List<ChannelPricing>> pricingsByProduct, BigDecimal defaultCtrPercent) {
    RepresentativeProduct representative = RepresentativeProduct
        .select(products, pricingsByProduct, defaultCtrPercent)
        .orElse(null);
    if (representative == null) {
      return ChannelComparisonItemResponse.from(channel, null, null);
    }
    EstimationPricing pricing = representative.pricing();
    BigDecimal cpcWon = pricing.pricingModel() == PricingModel.CPC ? pricing.value() : null;
    BigDecimal cpmWon = pricing.pricingModel() == PricingModel.CPM ? pricing.value() : null;
    return ChannelComparisonItemResponse.from(channel, cpcWon, cpmWon);
  }

  /**
   * 온보딩 예산과 캠페인 조건으로 맞춤 태그, 단가, 적합도, 예상 노출·클릭을 계산한다.
   */
  private ScoredItem personalizedItem(Onboarding onboarding, Channel channel,
      List<ChannelProduct> products, Map<UUID, List<ChannelPricing>> pricingsByProduct,
      long budgetWon, int periodDays, BigDecimal defaultCtrPercent) {
    MatchScore score = ChannelMatcher.match(onboarding, channel, products);
    List<String> tags = score.matchedAxes().stream()
        .limit(INSIGHT_TAG_LIMIT)
        .map(MatchAxis::name)
        .toList();

    BigDecimal cpcWon = null;
    BigDecimal cpmWon = null;
    ImpressionRange impressions = null;
    ClickRange clicks = null;
    boolean executable = false;

    RepresentativeProduct representative = RepresentativeProduct
        .select(products, pricingsByProduct, defaultCtrPercent)
        .orElse(null);
    if (representative != null) {
      EstimationPricing pricing = representative.pricing();
      cpcWon = pricing.pricingModel() == PricingModel.CPC ? pricing.value() : null;
      cpmWon = pricing.pricingModel() == PricingModel.CPM ? pricing.value() : null;
      if (budgetWon > 0) {
        EstimationResult result =
            EstimationService.estimate(representative.product(), budgetWon, periodDays);
        // 최소 단가보다 예산이 적으면 실행 불가능한 예상 노출·클릭 수를 비교 화면에 노출하지 않는다.
        if (result != null && result.isExecutable()) {
          ClickRange estimatedClicks = result.clicks();
          // 비교 화면에서는 과금 방식이 달라도 같은 기준으로 볼 수 있도록 클릭당 비용을 환산한다
          cpcWon = ClickCostPolicy.cpcWon(pricing, budgetWon, midpoint(estimatedClicks));
          impressions = result.impressions();
          clicks = estimatedClicks;
          executable = true;
        }
      }
    }

    return new ScoredItem(ChannelComparisonItemResponse.from(channel, tags, score.matchRate(),
        cpcWon, cpmWon, impressions, clicks), score.score(), executable);
  }

  /**
   * 채널별 비교에 필요한 상품을 일괄 조회한다.
   */
  private Map<UUID, List<ChannelProduct>> productsByChannel(List<Channel> channels) {
    List<UUID> channelIds = channels.stream().map(Channel::getId).toList();
    if (channelIds.isEmpty()) {
      return Map.of();
    }
    return channelProductRepository.findByChannelIdIn(channelIds).stream()
        .collect(Collectors.groupingBy(ChannelProduct::getChannelId));
  }

  /**
   * 대표 상품과 예상 노출·클릭 수 계산에 필요한 단가를 일괄 조회한다.
   */
  private Map<UUID, List<ChannelPricing>> pricingsByProduct(
      Map<UUID, List<ChannelProduct>> productsByChannel) {
    List<UUID> productIds = productsByChannel.values().stream()
        .flatMap(List::stream)
        .map(ChannelProduct::getId)
        .toList();
    if (productIds.isEmpty()) {
      return Map.of();
    }
    return channelPricingRepository.findByChannelProductIdIn(productIds).stream()
        .collect(Collectors.groupingBy(ChannelPricing::getChannelProductId));
  }

  /**
   * 예상 클릭 범위의 중앙값을 클릭당 비용 환산 기준으로 사용.
   */
  private static Long midpoint(ClickRange clicks) {
    return clicks == null ? null : Math.round((clicks.min() + clicks.max()) / 2.0);
  }

  /** 정렬에만 쓰는 배점·집행 가능 여부. */
  private record ScoredItem(ChannelComparisonItemResponse item, int score, boolean executable) {
  }
}
