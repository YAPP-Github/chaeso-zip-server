package chaeso.zip.server.comparison.application;

import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonItemResponse;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonResponse;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonSummaryResponse;
import chaeso.zip.server.comparison.application.dto.SavedChannelComparisonResponse;
import chaeso.zip.server.comparison.domain.ChannelComparisonNotFoundException;
import chaeso.zip.server.comparison.domain.ChannelComparisonSnapshot;
import chaeso.zip.server.comparison.domain.ChannelComparisonSnapshotFactory;
import chaeso.zip.server.comparison.domain.entity.ChannelComparison;
import chaeso.zip.server.comparison.domain.entity.ChannelComparisonItem;
import chaeso.zip.server.comparison.domain.repository.ChannelComparisonItemRepository;
import chaeso.zip.server.comparison.domain.repository.ChannelComparisonRepository;
import chaeso.zip.server.estimation.application.DefaultCtrProvider;
import chaeso.zip.server.estimation.domain.vo.PeriodDaysPolicy;
import chaeso.zip.server.onboarding.domain.OnboardingNotFoundException;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선택한 채널의 카탈로그 정보와 온보딩 맞춤 지표를 계산한다.
 *
 * <p>비로그인은 카탈로그 상세와 적합도, 예상 노출, 클릭을 {@link GuestChannelComparisonMocker}가
 * 채운 고정 MOCK 값으로 반환한다. 로그인한 뒤 온보딩이 있으면 고른 채널만 적합도순으로 정렬한다.
 *
 * <p>로그인했지만 온보딩이 없으면 예상 노출, 클릭은 기본값(100만원, 1개월)으로 계산한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelComparisonServiceImpl implements ChannelComparisonService {

  /** 저장된 비교의 순서 시작 번호 */
  private static final int FIRST_SORT_ORDER = 1;

  /**
   * 추천과 같은 비교 순서. 고른 채널에만 적용한다.
   *
   * <p>정규화 전 배점 합이 아니라 화면에 보이는 적합도로 정렬한다. 적용된 축 구성이 서로 다르면
   * (예: 대표 단가가 없어 예산 축을 채점하지 못한 채널) 배점 합의 순서와 적합도(%)의 순서가
   * 어긋나, 위에 놓인 채널이 더 낮은 %를 달고 나오게 된다.
   */
  private static final Comparator<ChannelComparisonSnapshot> BEST_FIRST = Comparator
      .comparingDouble(ChannelComparisonSnapshot::matchRateExact).reversed()
      .thenComparing(ChannelComparisonSnapshot::executable, Comparator.reverseOrder())
      .thenComparing(ChannelComparisonSnapshot::cpcWon,
          Comparator.nullsLast(Comparator.naturalOrder()))
      .thenComparing(ChannelComparisonSnapshot::estimatedClicks,
          Comparator.nullsLast(Comparator.reverseOrder()))
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
    boolean loggedIn = requesterId != null;
    List<ChannelComparisonSnapshot> snapshots =
        buildSnapshots(channelIds, onboardingId, requesterId, loggedIn);
    List<ChannelComparisonItemResponse> items = snapshots.stream()
        .map(ChannelComparisonItemResponse::from)
        .toList();

    return ChannelComparisonResponse.of(loggedIn ? items : GuestChannelComparisonMocker.mock(items));
  }

  /**
   * 비교 결과를 그대로 저장한다. 비교 횟수는 제한 X.
   * 온보딩이 없어도 기본값 기준 추정치 계산
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
    // save()는 인증된 사용자만 호출하므로 항상 로그인 상태로 취급한다.
    List<ChannelComparisonSnapshot> snapshots =
        buildSnapshots(channelIds, onboardingId, userId, true);

    ChannelComparison comparison = channelComparisonRepository.save(ChannelComparison.builder()
        .userId(userId)
        .onboardingId(onboardingId)
        .serviceName(onboardingId == null ? serviceName : null)
        .build());

    channelComparisonItemRepository.saveAll(IntStream.range(0, snapshots.size())
        .mapToObj(index -> ChannelComparisonItem.from(comparison.getId(),
            FIRST_SORT_ORDER + index, snapshots.get(index)))
        .toList());

    List<ChannelComparisonItemResponse> items = snapshots.stream()
        .map(ChannelComparisonItemResponse::from)
        .toList();
    return SavedChannelComparisonResponse.of(comparison.getId(), items);
  }

  /**
   * 선택한 채널 목록과 온보딩 조건으로 비교 스냅샷 목록을 생성한다.
   *
   * <p>온보딩이 없으면 정적/기본 추정 스냅샷을 요청 순서대로 생성하고,
   * 온보딩이 있으면 맞춤 지표를 계산, {@code loggedIn}일 때만 적합도순으로 정렬한다.
   * 비로그인(익명 온보딩)은 요청 순서를 유지한다.
   *
   * @param channelIds   비교할 채널 식별자 목록
   * @param onboardingId 온보딩 식별자 (선택)
   * @param requesterId  요청자 회원 식별자 (선택, 접근 가능한 온보딩인지 확인할 때만 사용)
   * @param loggedIn     로그인 여부. 정렬 및 기본 추정치 계산 기준
   * @return 생성 및 정렬된 채널 비교 스냅샷 목록
   */
  private List<ChannelComparisonSnapshot> buildSnapshots(List<UUID> channelIds, UUID onboardingId,
      UUID requesterId, boolean loggedIn) {
    List<Channel> channels = findChannels(channelIds);
    Map<UUID, List<ChannelProduct>> productsByChannel = productsByChannel(channels);
    Map<UUID, List<ChannelPricing>> pricingsByProduct = pricingsByProduct(productsByChannel);
    BigDecimal defaultCtrPercent = defaultCtrProvider.averageCtrPercent();

    if (onboardingId == null) {
      return channels.stream()
          .map(channel -> loggedIn
              ? ChannelComparisonSnapshotFactory.estimatedStaticSnapshot(channel,
                  productsByChannel.getOrDefault(channel.getId(), List.of()), pricingsByProduct,
                  defaultCtrPercent)
              : ChannelComparisonSnapshotFactory.staticSnapshot(channel,
                  productsByChannel.getOrDefault(channel.getId(), List.of()), pricingsByProduct,
                  defaultCtrPercent))
          .toList();
    }

    Onboarding onboarding = findAccessibleOnboarding(onboardingId, requesterId);
    int periodDays = PeriodDaysPolicy.daysOf(onboarding.getPeriod());
    long budgetWon = onboarding.getBudgetMax();

    List<ChannelComparisonSnapshot> personalized = channels.stream()
        .map(channel -> ChannelComparisonSnapshotFactory.personalizedSnapshot(onboarding, channel,
            productsByChannel.getOrDefault(channel.getId(), List.of()), pricingsByProduct,
            budgetWon, periodDays, defaultCtrPercent))
        .toList();
    return loggedIn ? personalized.stream().sorted(BEST_FIRST).toList() : personalized;
  }


  /**
   * 저장된 채널 비교를 조회한다.
   *
   * 남의 것을 조회했을 때도 404로 응답해, 그 id가 존재한다는 사실을 숨긴다.
   */
  @Override
  public SavedChannelComparisonResponse findComparison(UUID userId, UUID comparisonId) {
    ChannelComparison comparison = channelComparisonRepository.findById(comparisonId)
        .filter(c -> c.getUserId().equals(userId))
        .orElseThrow(() -> new ChannelComparisonNotFoundException(comparisonId));
    List<ChannelComparisonItemResponse> items = channelComparisonItemRepository
        .findByComparisonIdOrderBySortOrderAsc(comparison.getId()).stream()
        .map(ChannelComparisonItemResponse::from)
        .toList();
    return SavedChannelComparisonResponse.of(comparison.getId(), items);
  }

  /**
   * 저장된 비교를 페이지로 조회하고, 매체명과 서비스명을 채워 목록 요약으로 반환한다.
   *
   * @param userId   조회하는 사용자
   * @param pageable 페이지 요청 (최신순 고정)
   * @return 채널 비교 목록 요약
   */
  @Override
  public Page<ChannelComparisonSummaryResponse> findMyComparisons(UUID userId,
      Pageable pageable) {
    Page<ChannelComparison> comparisons =
        channelComparisonRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);

    List<UUID> comparisonIds =
        comparisons.getContent().stream().map(ChannelComparison::getId).toList();
    Map<UUID, List<ChannelComparisonItem>> itemsByComparison = comparisonIds.isEmpty()
        ? Map.of()
        : channelComparisonItemRepository
            .findByComparisonIdInOrderBySortOrderAsc(comparisonIds).stream()
            .collect(Collectors.groupingBy(ChannelComparisonItem::getComparisonId));

    List<UUID> onboardingIds = comparisons.getContent().stream()
        .map(ChannelComparison::getOnboardingId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    Map<UUID, String> onboardingServiceNames = onboardingIds.isEmpty()
        ? Map.of()
        : onboardingRepository.findAllById(onboardingIds).stream()
            .collect(Collectors.toMap(Onboarding::getId, Onboarding::getServiceName));

    return comparisons.map(comparison -> ChannelComparisonSummaryResponse.from(comparison,
        itemsByComparison.getOrDefault(comparison.getId(), List.of()), onboardingServiceNames));
  }

  private List<Channel> findChannels(List<UUID> channelIds) {
    Map<UUID, Channel> channels = channelRepository.findAllById(channelIds).stream()
        .filter(Channel::isActive)
        .collect(Collectors.toMap(Channel::getId, Function.identity()));
    return channelIds.stream()
        .map(id -> {
          Channel channel = channels.get(id);
          if (channel == null) {
            throw new ChannelNotFoundException(id);
          }
          return channel;
        })
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
   * 대표 상품과 예상 노출, 클릭 수 계산에 필요한 단가를 일괄 조회한다.
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
}
