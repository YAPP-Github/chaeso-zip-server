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
import chaeso.zip.server.comparison.application.dto.SavedChannelComparisonResponse;
import chaeso.zip.server.comparison.domain.ChannelComparisonSnapshot;
import chaeso.zip.server.comparison.domain.entity.ChannelComparison;
import chaeso.zip.server.comparison.domain.entity.ChannelComparisonItem;
import chaeso.zip.server.comparison.domain.repository.ChannelComparisonItemRepository;
import chaeso.zip.server.comparison.domain.repository.ChannelComparisonRepository;
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
import java.util.stream.IntStream;
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

  /** 저장된 비교의 순서 시작 번호 */
  private static final int FIRST_SORT_ORDER = 1;

  /** 추천과 같은 비교 순서. 고른 채널에만 적용한다. */
  private static final Comparator<ChannelComparisonSnapshot> BEST_FIRST = Comparator
      .comparingInt(ChannelComparisonSnapshot::score).reversed()
      .thenComparing(ChannelComparisonSnapshot::executable, Comparator.reverseOrder())
      .thenComparing(ChannelComparisonSnapshot::channelName);

  private final ChannelRepository channelRepository;
  private final ChannelProductRepository channelProductRepository;
  private final ChannelPricingRepository channelPricingRepository;
  private final OnboardingRepository onboardingRepository;
  private final ChannelComparisonRepository channelComparisonRepository;
  private final ChannelComparisonItemRepository channelComparisonItemRepository;
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
    List<Channel> channels = findChannels(channelIds);
    Map<UUID, List<ChannelProduct>> productsByChannel = productsByChannel(channels);
    Map<UUID, List<ChannelPricing>> pricingsByProduct = pricingsByProduct(productsByChannel);
    BigDecimal defaultCtrPercent = defaultCtrProvider.averageCtrPercent();
    boolean loggedIn = requesterId != null;

    if (onboardingId == null) {
      return ChannelComparisonResponse.of(channels.stream()
          .map(channel -> staticSnapshot(channel,
              productsByChannel.getOrDefault(channel.getId(), List.of()), pricingsByProduct,
              defaultCtrPercent))
          .map(ChannelComparisonItemResponse::from)
          .map(item -> loggedIn ? item : item.hideCatalogDetails())
          .toList());
    }

    Onboarding onboarding = findAccessibleOnboarding(onboardingId, requesterId);
    int periodDays = PeriodDaysPolicy.daysOf(onboarding.getPeriod());
    long budgetWon = onboarding.getBudgetMax();

    List<ChannelComparisonSnapshot> snapshots = channels.stream()
        .map(channel -> personalizedSnapshot(onboarding, channel,
            productsByChannel.getOrDefault(channel.getId(), List.of()), pricingsByProduct,
            budgetWon, periodDays, defaultCtrPercent))
        .toList();
    if (loggedIn) {
      return ChannelComparisonResponse.of(snapshots.stream()
          .sorted(BEST_FIRST)
          .map(ChannelComparisonItemResponse::from)
          .toList());
    }
    return ChannelComparisonResponse.of(snapshots.stream()
        .map(ChannelComparisonItemResponse::from)
        .map(ChannelComparisonItemResponse::hideCatalogDetails)
        .toList());
  }

  /**
   * 비교 결과를 그대로 저장한다. 비교 횟수는 제한 X
   *
   * @param userId       저장하는 사용자
   * @param channelIds   비교할 채널 식별자 목록 (2~3개)
   * @param onboardingId 비교 근거가 된 온보딩 (선택)
   * @param serviceName  onboardingId가 없을 때만 쓰는 서비스명
   * @return 저장된 채널 비교 응답 DTO
   */
  @Override
  @Transactional
  public SavedChannelComparisonResponse save(UUID userId, List<UUID> channelIds,
      UUID onboardingId, String serviceName) {
    List<Channel> channels = findChannels(channelIds);
    Map<UUID, List<ChannelProduct>> productsByChannel = productsByChannel(channels);
    Map<UUID, List<ChannelPricing>> pricingsByProduct = pricingsByProduct(productsByChannel);
    BigDecimal defaultCtrPercent = defaultCtrProvider.averageCtrPercent();

    List<ChannelComparisonSnapshot> snapshots;
    if (onboardingId == null) {
      snapshots = channels.stream()
          .map(channel -> staticSnapshot(channel,
              productsByChannel.getOrDefault(channel.getId(), List.of()), pricingsByProduct,
              defaultCtrPercent))
          .toList();
    } else {
      Onboarding onboarding = findAccessibleOnboarding(onboardingId, userId);
      int periodDays = PeriodDaysPolicy.daysOf(onboarding.getPeriod());
      long budgetWon = onboarding.getBudgetMax();
      snapshots = channels.stream()
          .map(channel -> personalizedSnapshot(onboarding, channel,
              productsByChannel.getOrDefault(channel.getId(), List.of()), pricingsByProduct,
              budgetWon, periodDays, defaultCtrPercent))
          .sorted(BEST_FIRST)
          .toList();
    }

    ChannelComparison comparison = channelComparisonRepository.save(ChannelComparison.builder()
        .userId(userId)
        .onboardingId(onboardingId)
        .serviceName(onboardingId == null ? serviceName : null)
        .build());

    channelComparisonItemRepository.saveAll(IntStream.range(0, snapshots.size())
        .mapToObj(index -> toItemEntity(comparison.getId(), FIRST_SORT_ORDER + index,
            snapshots.get(index)))
        .toList());

    return SavedChannelComparisonResponse.of(comparison.getId(), snapshots);
  }

  private List<Channel> findChannels(List<UUID> channelIds) {
    return channelIds.stream()
        .map(id -> channelRepository.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new ChannelNotFoundException(id)))
        .toList();
  }

  /**
   * 회원이 만든 온보딩이면 본인인지 확인한다. 익명 온보딩은 누구나 쓸 수 있다.
   */
  private Onboarding findAccessibleOnboarding(UUID onboardingId, UUID requesterId) {
    Onboarding onboarding = onboardingRepository.findById(onboardingId)
        .orElseThrow(() -> new OnboardingNotFoundException(onboardingId));
    if (onboarding.getUserId() != null && !onboarding.getUserId().equals(requesterId)) {
      throw new OnboardingNotFoundException(onboardingId);
    }
    return onboarding;
  }

  /**
   * 온보딩이 없을 때 보여 줄 채널 카탈로그 비교 스냅샷을 만든다.
   */
  private ChannelComparisonSnapshot staticSnapshot(Channel channel, List<ChannelProduct> products,
      Map<UUID, List<ChannelPricing>> pricingsByProduct, BigDecimal defaultCtrPercent) {
    List<String> pricingModelsAll = pricingModelsAll(products, pricingsByProduct);
    RepresentativeProduct representative = RepresentativeProduct
        .select(products, pricingsByProduct, defaultCtrPercent)
        .orElse(null);
    if (representative == null) {
      return ChannelComparisonSnapshot.catalogOnly(channel, channel.getDefaultTags(), null, null,
          pricingModelsAll);
    }
    EstimationPricing pricing = representative.pricing();
    BigDecimal cpcWon = pricing.pricingModel() == PricingModel.CPC ? pricing.value() : null;
    BigDecimal cpmWon = pricing.pricingModel() == PricingModel.CPM ? pricing.value() : null;
    return ChannelComparisonSnapshot.catalogOnly(channel, channel.getDefaultTags(), cpcWon, cpmWon,
        pricingModelsAll);
  }

  /**
   * 온보딩 예산과 캠페인 조건으로 맞춤 태그, 단가, 적합도, 예상 노출·클릭을 계산한다.
   */
  private ChannelComparisonSnapshot personalizedSnapshot(Onboarding onboarding, Channel channel,
      List<ChannelProduct> products, Map<UUID, List<ChannelPricing>> pricingsByProduct,
      long budgetWon, int periodDays, BigDecimal defaultCtrPercent) {
    MatchScore score = ChannelMatcher.match(onboarding, channel, products);
    List<String> tags = score.matchedAxes().stream()
        .limit(INSIGHT_TAG_LIMIT)
        .map(MatchAxis::name)
        .toList();
    List<String> pricingModelsAll = pricingModelsAll(products, pricingsByProduct);

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

    return ChannelComparisonSnapshot.matched(channel, score, tags, cpcWon, cpmWon,
        pricingModelsAll, executable, impressions, clicks);
  }

  private ChannelComparisonItem toItemEntity(UUID comparisonId, int sortOrder,
      ChannelComparisonSnapshot snapshot) {
    return ChannelComparisonItem.builder()
        .comparisonId(comparisonId)
        .channelId(snapshot.channelId())
        .sortOrder(sortOrder)
        .matchRate(snapshot.matchRate())
        .tagsSnap(snapshot.tags())
        .channelName(snapshot.channelName())
        .previewImageUrlSnap(snapshot.previewImageUrl())
        .displayPlatformsSnap(snapshot.displayPlatforms())
        .advantagesSnap(snapshot.advantages())
        .audienceSummarySnap(snapshot.audienceSummary())
        .adFormatsSnap(snapshot.adFormats())
        .targetingMethodsSnap(snapshot.targetingMethods())
        .executionTypeSnap(snapshot.executionType())
        .pricingModelsAll(snapshot.pricingModelsAll())
        .cpcWon(snapshot.cpcWon())
        .cpmWon(snapshot.cpmWon())
        .minBudgetWonSnap(snapshot.minBudgetWon())
        .estImpressionsMin(snapshot.impressions() == null ? null : snapshot.impressions().min())
        .estImpressionsMax(snapshot.impressions() == null ? null : snapshot.impressions().max())
        .estClicksMin(snapshot.clicks() == null ? null : snapshot.clicks().min())
        .estClicksMax(snapshot.clicks() == null ? null : snapshot.clicks().max())
        .build();
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
   * 채널이 그 시점에 가지고 있던 과금 방식 전체. 저장 스냅샷에만 쓴다.
   *
   * <p>대표 단가로 고르지 못한 상품의 과금 방식도 포함한다. 열거 순서로 정렬해 같은 채널이면 같은
   * 배열이 되게 한다.
   */
  private List<String> pricingModelsAll(List<ChannelProduct> products,
      Map<UUID, List<ChannelPricing>> pricingsByProduct) {
    return products.stream()
        .flatMap(product -> pricingsByProduct.getOrDefault(product.getId(), List.of()).stream())
        .map(ChannelPricing::getPricingModel)
        .distinct()
        .sorted()
        .map(PricingModel::name)
        .toList();
  }

  /**
   * 예상 클릭 범위의 중앙값을 클릭당 비용 환산 기준으로 사용.
   */
  private static Long midpoint(ClickRange clicks) {
    return clicks == null ? null : Math.round((clicks.min() + clicks.max()) / 2.0);
  }
}
