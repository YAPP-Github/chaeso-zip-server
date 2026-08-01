package chaeso.zip.server.recommendation.application;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
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
import chaeso.zip.server.recommendation.domain.ChannelMatcher;
import chaeso.zip.server.recommendation.domain.MatchScore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationServiceImpl implements RecommendationService {

  static final int MAX_ITEMS = 8;

  /**
   * 추천 순서
   */
  private static final Comparator<Recommendation> BEST_FIRST = Comparator
      .comparingInt((Recommendation recommendation) -> recommendation.score().score()).reversed()
      .thenComparing(recommendation -> recommendation.item().isExecutable(),
          Comparator.reverseOrder())
      .thenComparing(recommendation -> recommendation.item().channelName());

  private final OnboardingRepository onboardingRepository;
  private final ChannelRepository channelRepository;
  private final ChannelProductRepository channelProductRepository;
  private final ChannelPricingRepository channelPricingRepository;
  private final DefaultCtrProvider defaultCtrProvider;

  @Override
  public List<RecommendationItemResponse> recommend(UUID onboardingId) {
    Onboarding onboarding = onboardingRepository.findById(onboardingId)
        .orElseThrow(() -> new OnboardingBusinessException(
            OnboardingErrorCode.ONBOARDING_NOT_FOUND,
            "온보딩 정보가 없습니다. id=" + onboardingId));

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
        .map(Recommendation::item)
        .toList();
  }

  private Candidate toCandidate(Onboarding onboarding, Channel channel,
      List<ChannelProduct> products) {
    return new Candidate(channel, products, ChannelMatcher.match(onboarding, channel, products));
  }

  /**
   * 매칭된 채널 하나에 온보딩 예산·기간을 적용한다.
   */
  private Recommendation evaluate(Candidate candidate, Onboarding onboarding,
      Map<UUID, List<ChannelPricing>> pricingsByProduct, long budgetWon, int periodDays,
      BigDecimal defaultCtrPercent) {
    Channel channel = candidate.channel();
    MatchScore score = candidate.score();

    RepresentativeProduct representative = RepresentativeProduct
        .select(candidate.products(), pricingsByProduct, defaultCtrPercent)
        .orElse(null);
    if (representative == null) {
      return new Recommendation(score,
          RecommendationItemResponse.quoteRequired(channel, score, onboarding.getIndustry()));
    }

    long minBudgetWon = minBudgetWon(representative.pricing().value());
    boolean executable = budgetWon >= minBudgetWon;
    Long shortfallWon = executable ? null : minBudgetWon - budgetWon;
    long estimationBudgetWon = executable ? budgetWon : minBudgetWon;

    EstimationResult result =
        EstimationService.estimate(representative.product(), estimationBudgetWon, periodDays);
    if (result == null) {
      return new Recommendation(score,
          RecommendationItemResponse.quoteRequired(channel, score, onboarding.getIndustry()));
    }

    return new Recommendation(score, RecommendationItemResponse.estimated(channel, score,
        onboarding.getIndustry(), representative.pricing(), result, minBudgetWon, executable,
        shortfallWon, estimationBudgetWon));
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

  private record Recommendation(MatchScore score, RecommendationItemResponse item) {
  }
}
