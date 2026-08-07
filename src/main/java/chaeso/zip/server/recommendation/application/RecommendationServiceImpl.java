package chaeso.zip.server.recommendation.application;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.application.DefaultCtrProvider;
import chaeso.zip.server.estimation.domain.EstimationService;
import chaeso.zip.server.estimation.domain.RepresentativeProduct;
import chaeso.zip.server.estimation.domain.vo.EstimationResult;
import chaeso.zip.server.estimation.domain.vo.PeriodDaysPolicy;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
import chaeso.zip.server.recommendation.application.dto.SavedRecommendationResponse;
import chaeso.zip.server.recommendation.domain.ChannelMatcher;
import chaeso.zip.server.recommendation.domain.MatchScore;
import chaeso.zip.server.recommendation.domain.RecommendationSnapshot;
import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendation;
import chaeso.zip.server.recommendation.domain.repository.ChannelRecommendationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationServiceImpl implements RecommendationService {

  static final int MAX_ITEMS = 8;

  /** 추천 순위의 시작 번호 */
  private static final int FIRST_RANK = 1;

  /**
   * 추천 순서
   */
  private static final Comparator<Scored> BEST_FIRST = Comparator
      .comparingInt((Scored scored) -> scored.score().score()).reversed()
      .thenComparing(scored -> scored.snapshot().isExecutable(), Comparator.reverseOrder())
      .thenComparing(scored -> scored.snapshot().channelName());

  private final OnboardingRepository onboardingRepository;
  private final ChannelRepository channelRepository;
  private final ChannelProductRepository channelProductRepository;
  private final ChannelPricingRepository channelPricingRepository;
  private final ChannelRecommendationRepository channelRecommendationRepository;
  private final DefaultCtrProvider defaultCtrProvider;

  @Override
  public List<RecommendationItemResponse> recommend(UUID onboardingId) {
    return calculate(findOnboarding(onboardingId)).stream()
        .map(RecommendationItemResponse::from)
        .toList();
  }

  /**
   * 추천 결과를 스냅샷으로 저장한다.
   */
  @Override
  @Transactional
  public SavedRecommendationResponse save(UUID userId, UUID onboardingId) {
    Onboarding onboarding = findOwnedOnboarding(userId, onboardingId);
    List<RecommendationSnapshot> snapshots = calculate(onboarding);

    channelRecommendationRepository.deleteByOnboardingId(onboardingId);
    try {
      channelRecommendationRepository.saveAll(IntStream.range(0, snapshots.size())
          .mapToObj(index -> toEntity(userId, onboardingId, FIRST_RANK + index,
              snapshots.get(index)))
          .toList());
      channelRecommendationRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new OnboardingBusinessException(OnboardingErrorCode.CONCURRENT_SUBMISSION);
    }

    return SavedRecommendationResponse.of(onboardingId, snapshots);
  }

  private Onboarding findOnboarding(UUID onboardingId) {
    return onboardingRepository.findById(onboardingId)
        .orElseThrow(() -> new OnboardingBusinessException(
            OnboardingErrorCode.ONBOARDING_NOT_FOUND,
            "온보딩 정보가 없습니다. id=" + onboardingId));
  }

  private Onboarding findOwnedOnboarding(UUID userId, UUID onboardingId) {
    Onboarding onboarding = findOnboarding(onboardingId);
    if (!userId.equals(onboarding.getUserId())) {
      throw new OnboardingBusinessException(OnboardingErrorCode.ONBOARDING_NOT_FOUND,
          "온보딩 정보가 없습니다. id=" + onboardingId);
    }
    return onboarding;
  }

  private ChannelRecommendation toEntity(UUID userId, UUID onboardingId, int rank,
      RecommendationSnapshot snapshot) {
    return ChannelRecommendation.builder()
        .userId(userId)
        .onboardingId(onboardingId)
        .channelId(snapshot.channelId())
        .rank(rank)
        .score(snapshot.matchRate())
        .reason(snapshot.reason())
        .reasonTags(snapshot.reasonTags())
        .channelName(snapshot.channelName())
        .estPricingModel(snapshot.pricingModel())
        .estUnitPrice(snapshot.unitPrice())
        .estImpressionsMin(snapshot.impressions() == null ? null : snapshot.impressions().min())
        .estImpressionsMax(snapshot.impressions() == null ? null : snapshot.impressions().max())
        .estClicksMin(snapshot.clicks() == null ? null : snapshot.clicks().min())
        .estClicksMax(snapshot.clicks() == null ? null : snapshot.clicks().max())
        .cpcWon(snapshot.cpcWon())
        .pricingModelsAll(snapshot.pricingModelNames())
        .minBudgetWonSnap(snapshot.minBudgetWon())
        .audienceSummarySnap(snapshot.primaryTarget())
        .executable(snapshot.isExecutable())
        .shortfallWon(snapshot.shortfallWon())
        .build();
  }

  /**
   * 온보딩 하나에 대한 채널별 추천을 적합도 순으로 계산한다. 조회와 저장이 같은 결과를 쓴다.
   */
  private List<RecommendationSnapshot> calculate(Onboarding onboarding) {
    List<Channel> channels = channelRepository.findByActiveTrue();
    if (channels.isEmpty()) {
      return List.of();
    }

    Map<UUID, List<ChannelProduct>> productsByChannel = productsByChannel(channels);
    List<Candidate> candidates = channels.stream()
        .map(channel -> toCandidate(onboarding, channel,
            productsByChannel.getOrDefault(channel.getId(), List.of())))
        .filter(candidate -> candidate.score().isMatched())
        .toList();
    if (candidates.isEmpty()) {
      return List.of();
    }

    // 단가는 매칭된 채널의 상품에만 필요하다
    Map<UUID, List<ChannelPricing>> pricingsByProduct = pricingsByProduct(candidates);
    BigDecimal defaultCtrPercent = defaultCtrProvider.averageCtrPercent();
    int periodDays = PeriodDaysPolicy.daysOf(onboarding.getPeriod());
    long budgetWon = onboarding.getBudgetMax();

    return candidates.stream()
        .map(candidate ->
            evaluate(candidate, onboarding, pricingsByProduct, budgetWon, periodDays,
                defaultCtrPercent))
        .sorted(BEST_FIRST)
        .limit(MAX_ITEMS)
        .map(Scored::snapshot)
        .toList();
  }

  private Candidate toCandidate(Onboarding onboarding, Channel channel,
      List<ChannelProduct> products) {
    return new Candidate(channel, products, ChannelMatcher.match(onboarding, channel, products));
  }

  /**
   * 매칭된 채널 하나에 온보딩 예산·기간을 적용한다.
   */
  private Scored evaluate(Candidate candidate, Onboarding onboarding,
      Map<UUID, List<ChannelPricing>> pricingsByProduct, long budgetWon, int periodDays,
      BigDecimal defaultCtrPercent) {
    Channel channel = candidate.channel();
    MatchScore score = candidate.score();
    List<PricingModel> pricingModels = pricingModels(candidate, pricingsByProduct);

    RepresentativeProduct representative = RepresentativeProduct
        .select(candidate.products(), pricingsByProduct, defaultCtrPercent)
        .orElse(null);
    if (representative == null) {
      return new Scored(score, RecommendationSnapshot.quoteRequired(channel, score,
          onboarding.getIndustry(), pricingModels));
    }

    long minBudgetWon = minBudgetWon(representative.pricing().value());
    boolean executable = budgetWon >= minBudgetWon;
    Long shortfallWon = executable ? null : minBudgetWon - budgetWon;
    long estimationBudgetWon = executable ? budgetWon : minBudgetWon;

    EstimationResult result =
        EstimationService.estimate(representative.product(), estimationBudgetWon, periodDays);
    if (result == null) {
      return new Scored(score, RecommendationSnapshot.quoteRequired(channel, score,
          onboarding.getIndustry(), pricingModels));
    }

    return new Scored(score, RecommendationSnapshot.estimated(channel, score,
        onboarding.getIndustry(), representative.pricing(), pricingModels, result, minBudgetWon,
        executable, shortfallWon, estimationBudgetWon));
  }

  /**
   * 채널이 그 시점에 가지고 있던 과금 방식 전체. 저장 스냅샷에만 쓴다.
   *
   * <p>대표 단가로 고르지 못한 상품의 과금 방식도 포함한다. 열거 순서로 정렬해 같은 채널이면 같은
   * 배열이 되게 한다.
   */
  private List<PricingModel> pricingModels(Candidate candidate,
      Map<UUID, List<ChannelPricing>> pricingsByProduct) {
    return candidate.products().stream()
        .flatMap(product -> pricingsByProduct.getOrDefault(product.getId(), List.of()).stream())
        .map(ChannelPricing::getPricingModel)
        .distinct()
        .sorted()
        .toList();
  }

  private Map<UUID, List<ChannelProduct>> productsByChannel(List<Channel> channels) {
    List<UUID> channelIds = channels.stream().map(Channel::getId).toList();
    return channelProductRepository.findByChannelIdIn(channelIds).stream()
        .collect(Collectors.groupingBy(ChannelProduct::getChannelId));
  }

  private Map<UUID, List<ChannelPricing>> pricingsByProduct(List<Candidate> candidates) {
    List<UUID> productIds = candidates.stream()
        .flatMap(candidate -> candidate.products().stream())
        .map(ChannelProduct::getId)
        .toList();
    if (productIds.isEmpty()) {
      return Map.of();
    }
    return channelPricingRepository.findByChannelProductIdIn(productIds).stream()
        .collect(Collectors.groupingBy(ChannelPricing::getChannelProductId));
  }

  private static long minBudgetWon(BigDecimal price) {
    return price.setScale(0, RoundingMode.CEILING).longValue();
  }

  /** 적합도를 계산한 채널과 그 상품 목록. */
  private record Candidate(Channel channel, List<ChannelProduct> products, MatchScore score) {
  }

  private record Scored(MatchScore score, RecommendationSnapshot snapshot) {
  }
}
