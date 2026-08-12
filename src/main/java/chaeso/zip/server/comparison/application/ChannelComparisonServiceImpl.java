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
 * <p>온보딩이 없으면 채널 카탈로그와 대표 단가를 반환한다. 온보딩이 있으면 예산과 캠페인 조건을 적용해
 * 적합도와 예상 노출·클릭 수를 함께 반환한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelComparisonServiceImpl implements ChannelComparisonService {

  private final ChannelRepository channelRepository;
  private final ChannelProductRepository channelProductRepository;
  private final ChannelPricingRepository channelPricingRepository;
  private final OnboardingRepository onboardingRepository;
  private final DefaultCtrProvider defaultCtrProvider;

  /**
   * 선택된 채널 ID 목록을 받아 정적 또는 온보딩 맞춤 비교 응답을 생성한다.
   *
   * @param channelIds   비교할 채널 식별자 목록 (1~3개)
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

    if (onboardingId == null) {
      List<ChannelComparisonItemResponse> items = channels.stream()
          .map(channel -> staticItem(channel,
              productsByChannel.getOrDefault(channel.getId(), List.of()), pricingsByProduct,
              defaultCtrPercent))
          .toList();
      return ChannelComparisonResponse.of(items);
    }

    Onboarding onboarding = onboardingRepository.findById(onboardingId)
        .orElseThrow(() -> new OnboardingNotFoundException(onboardingId));
    if (onboarding.getUserId() != null && !onboarding.getUserId().equals(requesterId)) {
      throw new OnboardingNotFoundException(onboardingId);
    }
    int periodDays = PeriodDaysPolicy.daysOf(onboarding.getPeriod());
    long budgetWon = onboarding.getBudgetMax();

    List<ChannelComparisonItemResponse> items = channels.stream()
        .map(channel -> personalizedItem(onboarding, channel,
            productsByChannel.getOrDefault(channel.getId(), List.of()), pricingsByProduct,
            budgetWon, periodDays, defaultCtrPercent))
        .toList();
    return ChannelComparisonResponse.of(items);
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
   * 온보딩 예산과 캠페인 조건을 적용해 채널별 적합도와 예상 노출·클릭 수를 계산한다.
   */
  private ChannelComparisonItemResponse personalizedItem(Onboarding onboarding, Channel channel,
      List<ChannelProduct> products, Map<UUID, List<ChannelPricing>> pricingsByProduct,
      long budgetWon, int periodDays, BigDecimal defaultCtrPercent) {
    MatchScore score = ChannelMatcher.match(onboarding, channel, products);
    int matchRate = score.matchRate();
    List<String> tags = score.matchedAxes().stream()
        .limit(2)
        .map(MatchAxis::name)
        .toList();

    RepresentativeProduct representative = RepresentativeProduct
        .select(products, pricingsByProduct, defaultCtrPercent)
        .orElse(null);
    if (representative == null) {
      return ChannelComparisonItemResponse.from(channel, tags, matchRate, null, null,
          null, null);
    }

    EstimationPricing pricing = representative.pricing();
    BigDecimal fixedCpcWon = pricing.pricingModel() == PricingModel.CPC ? pricing.value() : null;
    BigDecimal cpmWon = pricing.pricingModel() == PricingModel.CPM ? pricing.value() : null;
    if (budgetWon <= 0) {
      return ChannelComparisonItemResponse.from(channel, tags, matchRate, fixedCpcWon, cpmWon,
          null, null);
    }

    EstimationResult result =
        EstimationService.estimate(representative.product(), budgetWon, periodDays);
    if (result == null) {
      return ChannelComparisonItemResponse.from(channel, tags, matchRate, null, null,
          null, null);
    }

    // 최소 단가보다 예산이 적으면 실행 불가능한 예상 노출·클릭 수를 비교 화면에 노출하지 않는다.
    if (!result.isExecutable()) {
      return ChannelComparisonItemResponse.from(channel, tags, matchRate, fixedCpcWon, cpmWon,
          null, null);
    }

    ClickRange clicks = result.clicks();
    ImpressionRange impressions = result.impressions();

    // 비교 화면에서는 과금 방식이 달라도 같은 기준으로 볼 수 있도록 클릭당 비용을 환산한다
    BigDecimal cpcWon =
        ClickCostPolicy.cpcWon(pricing, budgetWon, midpoint(clicks));

    return ChannelComparisonItemResponse.from(channel, tags, matchRate, cpcWon, cpmWon,
        impressions, clicks);
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
}
