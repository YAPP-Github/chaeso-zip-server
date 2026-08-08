package chaeso.zip.server.channel.application;

import chaeso.zip.server.channel.application.dto.AudienceMetricResponse;
import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.channel.application.dto.PricingResponse;
import chaeso.zip.server.channel.application.dto.ProductResponse;
import chaeso.zip.server.channel.application.dto.RecommendationBasisResponse;
import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.entity.ChannelReference;
import chaeso.zip.server.channel.domain.repository.ChannelAudienceMetricRepository;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelReferenceRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.estimation.domain.EstimationService;
import chaeso.zip.server.estimation.domain.vo.EstimationProduct;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.recommendation.domain.repository.ChannelRecommendationRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    List<ChannelProduct> channelProducts = channelProductRepository.findByChannelId(id);
    Map<UUID, List<PricingResponse>> pricingByProductId = pricingByProductId(channelProducts);
    List<ProductResponse> products = channelProducts.stream()
        .map(product -> ProductResponse.from(product,
            pricingByProductId.getOrDefault(product.getId(), List.of()),
            expectedClicks(product)))
        .toList();

    List<AudienceMetricResponse> audienceMetrics =
        channelAudienceMetricRepository.findByChannelId(id).stream()
            .map(AudienceMetricResponse::from)
            .toList();

    List<String> references = channelReferenceRepository.findByChannelId(id).stream()
        .map(ChannelReference::getResultText)
        .filter(Objects::nonNull)
        .toList();

    return ChannelDetailResponse.from(channel, products, audienceMetrics, references,
        recommendationBasis(id, onboardingId, requesterId));
  }

  /**
   * 추천 근거가 된 온보딩 선택지
   */
  private RecommendationBasisResponse recommendationBasis(UUID channelId, UUID onboardingId,
      UUID requesterId) {
    if (onboardingId == null || requesterId == null) {
      return null;
    }
    boolean recommended = channelRecommendationRepository
        .existsByOnboardingIdAndChannelId(onboardingId, channelId);
    if (!recommended) {
      return null;
    }
    return onboardingRepository.findById(onboardingId)
        .filter(onboarding -> requesterId.equals(onboarding.getUserId()))
        .map(RecommendationBasisResponse::from)
        .orElse(null);
  }

  /**
   * 상품의 기대 노출에 대표 CTR 을 적용한 예상 클릭 수
   * CTR 이 없는 상품은 기본 CTR 로 채우지 않고 값을 비운다.
   */
  private static Long expectedClicks(ChannelProduct product) {
    return EstimationService.estimateClicks(product.getExpectedImpressions(),
        EstimationProduct.ctrPercentOf(product, null));
  }

  private Map<UUID, List<PricingResponse>> pricingByProductId(List<ChannelProduct> products) {
    if (products.isEmpty()) {
      return Map.of();
    }
    List<UUID> productIds = products.stream().map(ChannelProduct::getId).toList();
    return channelPricingRepository.findByChannelProductIdIn(productIds).stream()
        .collect(Collectors.groupingBy(
            ChannelPricing::getChannelProductId,
            Collectors.mapping(PricingResponse::from, Collectors.toList())));
  }
}
