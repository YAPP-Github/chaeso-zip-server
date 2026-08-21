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
import chaeso.zip.server.estimation.domain.vo.ClickRange;
import chaeso.zip.server.estimation.domain.vo.EstimationResult;
import chaeso.zip.server.estimation.domain.vo.PeriodDaysPolicy;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.onboarding.domain.OnboardingNotFoundException;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
import chaeso.zip.server.recommendation.application.dto.RecommendationSummaryResponse;
import chaeso.zip.server.recommendation.application.dto.SavedRecommendationResponse;
import chaeso.zip.server.recommendation.domain.BudgetFit;
import chaeso.zip.server.recommendation.domain.ChannelMatcher;
import chaeso.zip.server.recommendation.domain.MatchAxis;
import chaeso.zip.server.recommendation.domain.MatchScore;
import chaeso.zip.server.recommendation.domain.RecommendationNotFoundException;
import chaeso.zip.server.recommendation.domain.RecommendationSnapshot;
import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendation;
import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendationResult;
import chaeso.zip.server.recommendation.domain.repository.ChannelRecommendationRepository;
import chaeso.zip.server.recommendation.domain.repository.ChannelRecommendationResultRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
      .comparingDouble((Scored scored) -> scored.score().matchRateExact()).reversed()
      .thenComparing(scored -> scored.snapshot().isExecutable(), Comparator.reverseOrder())
      .thenComparing(scored -> scored.snapshot().cpcWon(),
          Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(Scored::estimatedClicks, Comparator.nullsLast(Comparator.reverseOrder()))
      .thenComparing(scored -> scored.snapshot().channelName());

  private final OnboardingRepository onboardingRepository;
  private final ChannelRepository channelRepository;
  private final ChannelProductRepository channelProductRepository;
  private final ChannelPricingRepository channelPricingRepository;
  private final ChannelRecommendationRepository channelRecommendationRepository;
  private final ChannelRecommendationResultRepository channelRecommendationResultRepository;
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
  public SavedRecommendationResponse save(UUID userId, UUID onboardingId, String serviceName) {
    Onboarding onboarding = findOwnedOnboarding(userId, onboardingId);
    List<RecommendationSnapshot> snapshots = calculate(onboarding);

    onboardingRepository.findByIdForUpdate(onboardingId);

    channelRecommendationRepository.deleteByOnboardingId(onboardingId);
    channelRecommendationResultRepository.deleteByOnboardingId(onboardingId);
    UUID resultId;
    try {
      resultId = channelRecommendationResultRepository.save(ChannelRecommendationResult.builder()
          .userId(userId)
          .onboardingId(onboardingId)
          .serviceName(serviceName)
          .build()).getId();
      channelRecommendationRepository.saveAll(IntStream.range(0, snapshots.size())
          .mapToObj(index -> toEntity(userId, onboardingId, resultId, FIRST_RANK + index,
              snapshots.get(index)))
          .toList());
      channelRecommendationRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new OnboardingBusinessException(OnboardingErrorCode.CONCURRENT_SUBMISSION);
    }

    return SavedRecommendationResponse.of(resultId, onboardingId, snapshots);
  }

  @Override
  public Page<RecommendationSummaryResponse> findMyRecommendations(UUID userId, Pageable pageable) {
    Page<ChannelRecommendationResult> results =
        channelRecommendationResultRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId,
            pageable);

    Map<UUID, List<ChannelRecommendation>> itemsByResult = itemsOf(
        results.getContent().stream().map(ChannelRecommendationResult::getId).toList());

    return results.map(result -> RecommendationSummaryResponse.from(result,
        itemsByResult.getOrDefault(result.getId(), List.of())));
  }

  @Override
  public List<RecommendationItemResponse> findRecommendation(UUID userId, UUID recommendationId) {
    ChannelRecommendationResult result = channelRecommendationResultRepository
        .findById(recommendationId)
        .filter(saved -> saved.getUserId().equals(userId))
        .orElseThrow(() -> new RecommendationNotFoundException(recommendationId));

    return restore(channelRecommendationRepository.findByResultIdOrderByRankAsc(result.getId()));
  }

  private List<RecommendationItemResponse> restore(List<ChannelRecommendation> items) {
    Map<UUID, String> channelNames = channelNames(items.stream()
        .map(ChannelRecommendation::getChannelId)
        .distinct()
        .toList());

    return items.stream()
        .map(item -> RecommendationItemResponse.from(item,
            channelNames.getOrDefault(item.getChannelId(), item.getChannelName())))
        .toList();
  }

  private Map<UUID, String> channelNames(List<UUID> channelIds) {
    if (channelIds.isEmpty()) {
      return Map.of();
    }
    return channelRepository.findAllById(channelIds).stream()
        .collect(Collectors.toMap(Channel::getId, Channel::getName));
  }

  private Map<UUID, List<ChannelRecommendation>> itemsOf(List<UUID> resultIds) {
    if (resultIds.isEmpty()) {
      return Map.of();
    }
    return channelRecommendationRepository.findByResultIdInOrderByRankAsc(resultIds).stream()
        .collect(Collectors.groupingBy(ChannelRecommendation::getResultId));
  }

  private Onboarding findOnboarding(UUID onboardingId) {
    return onboardingRepository.findById(onboardingId)
        .orElseThrow(() -> new OnboardingNotFoundException(onboardingId));
  }

  private Onboarding findOwnedOnboarding(UUID userId, UUID onboardingId) {
    Onboarding onboarding = findOnboarding(onboardingId);
    if (!userId.equals(onboarding.getUserId())) {
      throw new OnboardingNotFoundException(onboardingId);
    }
    return onboarding;
  }

  private ChannelRecommendation toEntity(UUID userId, UUID onboardingId, UUID resultId,
      int rank, RecommendationSnapshot snapshot) {
    return ChannelRecommendation.builder()
        .userId(userId)
        .onboardingId(onboardingId)
        .resultId(resultId)
        .channelId(snapshot.channelId())
        .rank(rank)
        .score(snapshot.matchRate())
        .reason(snapshot.reason())
        .reasonTags(snapshot.reasonTags())
        .channelName(snapshot.channelName())
        .wordmarkUrlSnap(snapshot.wordmarkUrl())
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

    return candidates.stream()
        .map(candidate ->
            evaluate(candidate, onboarding, pricingsByProduct, periodDays, defaultCtrPercent))
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
   * 매칭된 채널 하나에 온보딩 예산·기간을 적용해 예산 축까지 채운 적합도를 낸다.
   *
   * <p>예산 축은 대표 단가를 알아야 채점할 수 있어 캠페인 조건 축과 따로 붙인다. 단가가 없어
   * 집행 금액을 모르는 채널은 예산 축을 근거 없음으로 두고 적합도 신뢰도를 낮춘다.
   */
  private Scored evaluate(Candidate candidate, Onboarding onboarding,
      Map<UUID, List<ChannelPricing>> pricingsByProduct, int periodDays,
      BigDecimal defaultCtrPercent) {
    List<PricingModel> pricingModels = pricingModels(candidate, pricingsByProduct);
    long budgetWon = onboarding.getBudgetMax();

    RepresentativeProduct representative = RepresentativeProduct
        .select(candidate.products(), pricingsByProduct, defaultCtrPercent)
        .orElse(null);
    if (representative == null) {
      return quoteRequired(candidate, onboarding, pricingModels);
    }

    long minBudgetWon = minBudgetWon(representative.pricing().value());
    boolean executable = budgetWon >= minBudgetWon;
    Long shortfallWon = executable ? null : minBudgetWon - budgetWon;
    long estimationBudgetWon = executable ? budgetWon : minBudgetWon;

    EstimationResult result =
        EstimationService.estimate(representative.product(), estimationBudgetWon, periodDays);
    if (result == null) {
      return quoteRequired(candidate, onboarding, pricingModels);
    }

    MatchScore score = candidate.score().with(MatchAxis.BUDGET,
        BudgetFit.of(onboarding.getBudgetMin(), budgetWon, minBudgetWon));
    return new Scored(score, RecommendationSnapshot.estimated(candidate.channel(), score,
        onboarding.getIndustry(), representative.pricing(), pricingModels, result, minBudgetWon,
        executable, shortfallWon, estimationBudgetWon));
  }

  /** 집행 금액을 알 수 없는 채널. 예산 축을 채점하지 못한 만큼 적합도 신뢰도가 낮아진다. */
  private Scored quoteRequired(Candidate candidate, Onboarding onboarding,
      List<PricingModel> pricingModels) {
    MatchScore score = candidate.score().withUnknown(MatchAxis.BUDGET);
    return new Scored(score, RecommendationSnapshot.quoteRequired(candidate.channel(), score,
        onboarding.getIndustry(), pricingModels));
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

    /** 정렬에 쓰는 예상 클릭 중앙값. 클릭을 추정할 수 없으면 {@code null} */
    private Long estimatedClicks() {
      ClickRange clicks = snapshot.clicks();
      return clicks == null ? null : Math.round((clicks.min() + clicks.max()) / 2.0);
    }
  }
}
