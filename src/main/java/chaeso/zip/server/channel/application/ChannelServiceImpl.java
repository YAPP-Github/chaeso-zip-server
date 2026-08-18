package chaeso.zip.server.channel.application;

import chaeso.zip.server.channel.application.dto.AudienceMetricResponse;
import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.channel.application.dto.PricingResponse;
import chaeso.zip.server.channel.application.dto.ProductResponse;
import chaeso.zip.server.channel.application.dto.RecommendationBasisResponse;
import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelAudienceMetric;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.entity.ChannelReference;
import chaeso.zip.server.channel.domain.repository.ChannelAudienceMetricRepository;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelReferenceRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.channel.domain.vo.AudienceMetricCategory;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.estimation.domain.EstimationService;
import chaeso.zip.server.estimation.domain.vo.EstimationProduct;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.recommendation.domain.repository.ChannelRecommendationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

  private static final int MAX_TAGS = 2;
  private static final int MAX_AUDIENCE_METRICS = 2;

  /**
   * 대표 오디언스 지표 선택 순서
   */
  private static final Comparator<ChannelAudienceMetric> REPRESENTATIVE_FIRST = Comparator
      .comparing(ChannelServiceImpl::categoryOf)
      .thenComparing(ChannelAudienceMetric::getValueNumeric,
          Comparator.nullsLast(Comparator.reverseOrder()))
      .thenComparing(ChannelAudienceMetric::getMetricName,
          Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(ChannelAudienceMetric::getId,
          Comparator.nullsLast(Comparator.naturalOrder()));

  private final ChannelRepository channelRepository;
  private final ChannelProductRepository channelProductRepository;
  private final ChannelPricingRepository channelPricingRepository;
  private final ChannelAudienceMetricRepository channelAudienceMetricRepository;
  private final ChannelReferenceRepository channelReferenceRepository;
  private final ChannelRecommendationRepository channelRecommendationRepository;
  private final OnboardingRepository onboardingRepository;

  @Override
  @Transactional(readOnly = true)
  public Page<ChannelListItemResponse> getChannels(String name, List<Category> primaryCategories,
      Pageable pageable) {
    return channelRepository.searchActiveChannels(name, primaryCategories, pageable)
        .map(ChannelListItemResponse::from);
  }

  @Override
  @Transactional(readOnly = true)
  public ChannelDetailResponse getChannel(UUID id, UUID onboardingId, UUID requesterId) {
    Channel channel = channelRepository.findByIdAndActiveTrue(id)
        .orElseThrow(() -> new ChannelNotFoundException(id));

    Onboarding onboarding = ownedOnboarding(onboardingId, requesterId);

    List<ChannelProduct> channelProducts = channelProductRepository.findByChannelId(id);
    Map<UUID, List<ChannelPricing>> pricingsByProductId = pricingsByProductId(channelProducts);
    Long budgetWon = executabilityBudgetWon(onboarding);
    List<ProductResponse> products = channelProducts.stream()
        .map(product -> productResponse(product,
            pricingsByProductId.getOrDefault(product.getId(), List.of()), budgetWon))
        .toList();

    List<AudienceMetricResponse> audienceMetrics =
        representativeAudienceMetrics(channelAudienceMetricRepository.findByChannelId(id));

    List<String> references = channelReferenceRepository.findByChannelId(id).stream()
        .map(ChannelReference::getResultText)
        .filter(Objects::nonNull)
        .toList();

    return ChannelDetailResponse.from(channel, products, audienceMetrics, references,
        recommendationBasis(id, onboardingId, onboarding), tags(channel));
  }

  private Onboarding ownedOnboarding(UUID onboardingId, UUID requesterId) {
    if (onboardingId == null || requesterId == null) {
      return null;
    }
    return onboardingRepository.findById(onboardingId)
        .filter(onboarding -> requesterId.equals(onboarding.getUserId()))
        .orElse(null);
  }

  /**
   * 추천 근거가 된 온보딩 선택지
   */
  private RecommendationBasisResponse recommendationBasis(UUID channelId, UUID onboardingId,
      Onboarding onboarding) {
    if (onboarding == null) {
      return null;
    }
    boolean recommended = channelRecommendationRepository
        .existsByOnboardingIdAndChannelId(onboardingId, channelId);
    return recommended ? RecommendationBasisResponse.from(onboarding) : null;
  }

  /**
   * "이런 점이 좋아요" 매체 키워드. 채널 고유의 키워드이므로 맞춤 여부와 무관하게 누구에게나 같은 값을
   * 주고, 화면이 두 개까지만 담으므로 앞의 두 개로 자른다.
   */
  private static List<String> tags(Channel channel) {
    List<String> defaultTags = channel.getDefaultTags();
    if (defaultTags == null || defaultTags.size() <= MAX_TAGS) {
      return defaultTags;
    }
    return List.copyOf(defaultTags.subList(0, MAX_TAGS));
  }

  /**
   * 채널이 가진 지표 중 카테고리 우선순위가 높은 것부터 서로 다른 카테고리로 두 개를 고른다.
   * 지표명을 정규화하지 않고 원본을 그대로 주며, 카테고리가 하나뿐인 채널은 그 카테고리에서 두 개를 고른다.
   */
  private static List<AudienceMetricResponse> representativeAudienceMetrics(
      List<ChannelAudienceMetric> metrics) {
    List<ChannelAudienceMetric> byPriority = metrics.stream()
        .sorted(REPRESENTATIVE_FIRST)
        .toList();

    List<ChannelAudienceMetric> selected = new ArrayList<>(MAX_AUDIENCE_METRICS);
    Set<AudienceMetricCategory> pickedCategories = EnumSet.noneOf(AudienceMetricCategory.class);
    for (ChannelAudienceMetric metric : byPriority) {
      if (selected.size() == MAX_AUDIENCE_METRICS) {
        break;
      }
      if (pickedCategories.add(categoryOf(metric))) {
        selected.add(metric);
      }
    }
    for (ChannelAudienceMetric metric : byPriority) {
      if (selected.size() == MAX_AUDIENCE_METRICS) {
        break;
      }
      if (!selected.contains(metric)) {
        selected.add(metric);
      }
    }
    return selected.stream().map(AudienceMetricResponse::from).toList();
  }

  private static AudienceMetricCategory categoryOf(ChannelAudienceMetric metric) {
    return AudienceMetricCategory.of(metric.getMetricName());
  }

  private static ProductResponse productResponse(ChannelProduct product,
      List<ChannelPricing> pricings, Long budgetWon) {
    return ProductResponse.from(product,
        pricings.stream().map(PricingResponse::from).toList(),
        expectedClicks(product),
        isExecutable(product, pricings, budgetWon));
  }

  private static Boolean isExecutable(ChannelProduct product, List<ChannelPricing> pricings,
      Long budgetWon) {
    if (budgetWon == null) {
      return null;
    }
    return EstimationService.isExecutable(EstimationProduct.from(product, pricings), budgetWon);
  }

  /**
   * 집행 가능 판정의 기준이 되는 예산(원)
   */
  private static Long executabilityBudgetWon(Onboarding onboarding) {
    return onboarding == null ? null : onboarding.getBudgetMax();
  }

  /**
   * 상품의 기대 노출에 대표 CTR 을 적용한 예상 클릭 수
   * CTR 이 없는 상품은 기본 CTR 로 채우지 않고 값을 비운다.
   */
  private static Long expectedClicks(ChannelProduct product) {
    return EstimationService.estimateClicks(product.getExpectedImpressions(),
        EstimationProduct.ctrPercentOf(product, null));
  }

  private Map<UUID, List<ChannelPricing>> pricingsByProductId(List<ChannelProduct> products) {
    if (products.isEmpty()) {
      return Map.of();
    }
    List<UUID> productIds = products.stream().map(ChannelProduct::getId).toList();
    return channelPricingRepository.findByChannelProductIdIn(productIds).stream()
        .collect(Collectors.groupingBy(ChannelPricing::getChannelProductId));
  }
}
