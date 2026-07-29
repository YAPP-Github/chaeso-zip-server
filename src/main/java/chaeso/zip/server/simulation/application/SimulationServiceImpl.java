package chaeso.zip.server.simulation.application;

import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.estimation.domain.EstimationService;
import chaeso.zip.server.estimation.domain.vo.EstimationPricing;
import chaeso.zip.server.estimation.domain.vo.EstimationProduct;
import chaeso.zip.server.estimation.domain.vo.EstimationResult;
import chaeso.zip.server.simulation.application.dto.AllocationCommand;
import chaeso.zip.server.simulation.application.dto.CountRangeResponse;
import chaeso.zip.server.simulation.application.dto.SimulationCommand;
import chaeso.zip.server.simulation.application.dto.SimulationItemResponse;
import chaeso.zip.server.simulation.application.dto.SimulationResponse;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulation;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulationItem;
import chaeso.zip.server.simulation.domain.repository.BudgetSimulationItemRepository;
import chaeso.zip.server.simulation.domain.repository.BudgetSimulationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimulationServiceImpl implements SimulationService {

  private final ChannelRepository channelRepository;
  private final ChannelProductRepository channelProductRepository;
  private final ChannelPricingRepository channelPricingRepository;
  private final BudgetSimulationRepository budgetSimulationRepository;
  private final BudgetSimulationItemRepository budgetSimulationItemRepository;

  /**
   * 대표 상품 선택 순서
   */
  private static final Comparator<Candidate> BEST_PRODUCT = Comparator
      .comparing(Candidate::estimatesImpressions, Comparator.reverseOrder())
      .thenComparing(candidate -> candidate.pricing().value())
      .thenComparing(Candidate::productId);

  private final DefaultCtrProvider defaultCtrProvider;

  @Override
  public SimulationResponse estimate(SimulationCommand command) {
    return calculate(command);
  }

  @Override
  @Transactional
  public SimulationResponse save(UUID userId, SimulationCommand command) {
    SimulationResponse calculated = calculate(command);

    BudgetSimulation simulation = budgetSimulationRepository.save(BudgetSimulation.builder()
        .userId(userId)
        .totalBudgetWon(calculated.totalBudgetWon())
        .period(calculated.period())
        .totalEstImpressions(calculated.totalEstImpressions())
        .totalEstClicks(calculated.totalEstClicks())
        .build());

    List<SimulationItemResponse> items = calculated.items();
    budgetSimulationItemRepository.saveAll(IntStream.range(0, items.size())
        .mapToObj(order -> toSnapshot(simulation.getId(), order, items.get(order)))
        .toList());

    return calculated.withSimulationId(simulation.getId());
  }

  @Override
  public Optional<SimulationResponse> findLatest(UUID userId) {
    return budgetSimulationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
        .map(this::restore);
  }

  private SimulationResponse restore(BudgetSimulation simulation) {
    List<BudgetSimulationItem> items = budgetSimulationItemRepository
        .findByBudgetSimulationIdOrderBySortOrderAsc(simulation.getId());
    Map<UUID, String> channelNames = channelNames(items.stream()
        .map(BudgetSimulationItem::getChannelId)
        .distinct()
        .toList());

    return SimulationResponse.from(simulation, items.stream()
        .map(item -> SimulationItemResponse.from(item, channelNames.get(item.getChannelId())))
        .toList());
  }

  private SimulationResponse calculate(SimulationCommand command) {
    int periodDays = command.period().getDays();
    BigDecimal defaultCtrPercent = defaultCtrProvider.averageCtrPercent();

    List<UUID> channelIds = command.allocations().stream()
        .map(AllocationCommand::channelId)
        .distinct()
        .toList();
    Map<UUID, Channel> channels = loadChannels(channelIds);
    Map<UUID, List<ChannelProduct>> productsByChannel = channelProductRepository
        .findByChannelIdIn(channelIds).stream()
        .collect(Collectors.groupingBy(ChannelProduct::getChannelId));
    Map<UUID, List<ChannelPricing>> pricingsByProduct = loadPricings(productsByChannel);

    List<SimulationItemResponse> items = command.allocations().stream()
        .map(allocation -> evaluate(
            allocation,
            channels.get(allocation.channelId()).getName(),
            productsByChannel.getOrDefault(allocation.channelId(), List.of()),
            pricingsByProduct,
            periodDays,
            defaultCtrPercent))
        .toList();

    return SimulationResponse.of(
        command.totalBudgetWon(),
        command.period(),
        total(items, SimulationItemResponse::estImpressions),
        total(items, SimulationItemResponse::estClicks),
        items);
  }

  private SimulationItemResponse evaluate(AllocationCommand allocation, String channelName,
      List<ChannelProduct> products, Map<UUID, List<ChannelPricing>> pricingsByProduct,
      int periodDays, BigDecimal defaultCtrPercent) {
    UUID channelId = allocation.channelId();

    Candidate candidate = products.stream()
        .map(product -> toCandidate(product,
            pricingsByProduct.getOrDefault(product.getId(), List.of()), defaultCtrPercent))
        .filter(Objects::nonNull)
        .min(BEST_PRODUCT)
        .orElse(null);

    if (candidate == null) {
      return SimulationItemResponse.quoteRequired(channelId, channelName, allocation.budgetWon(),
          allocation.allocationPct());
    }

    BigDecimal price = candidate.pricing().value();
    if (allocation.budgetWon() <= 0) {
      return SimulationItemResponse.notAllocated(channelId, channelName, candidate.productId(),
          allocation.allocationPct(), candidate.pricing(), shortfallWon(price, 0));
    }

    EstimationResult result = EstimationService.estimate(candidate.product(),
        allocation.budgetWon(), periodDays);
    if (result == null) {
      return SimulationItemResponse.quoteRequired(channelId, channelName, allocation.budgetWon(),
          allocation.allocationPct());
    }

    Long shortfall = result.isExecutable() ? null : shortfallWon(price, allocation.budgetWon());
    return SimulationItemResponse.estimated(channelId, channelName, candidate.productId(),
        allocation.budgetWon(), allocation.allocationPct(), candidate.pricing(), result, shortfall);
  }

  private static Candidate toCandidate(ChannelProduct product, List<ChannelPricing> pricings,
      BigDecimal defaultCtrPercent) {
    EstimationProduct estimationProduct =
        EstimationProduct.from(product, pricings, defaultCtrPercent);
    EstimationPricing pricing = EstimationService.representativePricing(estimationProduct);
    return pricing == null ? null : new Candidate(product.getId(), estimationProduct, pricing,
        EstimationService.estimatesImpressions(estimationProduct));
  }

  private Map<UUID, Channel> loadChannels(List<UUID> channelIds) {
    Map<UUID, Channel> channels = channelRepository.findAllById(channelIds).stream()
        .collect(Collectors.toMap(Channel::getId, Function.identity()));
    channelIds.stream()
        .filter(id -> !channels.containsKey(id))
        .findFirst()
        .ifPresent(id -> {
          throw new ChannelNotFoundException(id);
        });
    return channels;
  }

  private Map<UUID, String> channelNames(List<UUID> channelIds) {
    return channelRepository.findAllById(channelIds).stream()
        .collect(Collectors.toMap(Channel::getId, Channel::getName));
  }

  private Map<UUID, List<ChannelPricing>> loadPricings(
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

  /** 집행 가능한 매체들의 범위 중앙값을 더한 합계 */
  private static long total(List<SimulationItemResponse> items,
      Function<SimulationItemResponse, CountRangeResponse> range) {
    return items.stream()
        .filter(SimulationItemResponse::isExecutable)
        .map(range)
        .filter(Objects::nonNull)
        .mapToLong(CountRangeResponse::midpoint)
        .sum();
  }

  /** 집행에 부족한 금액 */
  private static Long shortfallWon(BigDecimal price, long budgetWon) {
    return price.subtract(BigDecimal.valueOf(budgetWon))
        .setScale(0, RoundingMode.CEILING)
        .longValue();
  }

  private static BudgetSimulationItem toSnapshot(UUID simulationId, int sortOrder,
      SimulationItemResponse item) {
    BudgetSimulationItem.BudgetSimulationItemBuilder builder = BudgetSimulationItem.builder()
        .budgetSimulationId(simulationId)
        .channelId(item.channelId())
        .channelProductId(item.channelProductId())
        .sortOrder(sortOrder)
        .allocatedBudgetWon(item.allocatedBudgetWon())
        .allocationPct(item.allocationPct())
        .cpcWon(item.cpcWon())
        .cpmWon(item.cpmWon())
        .executable(item.isExecutable())
        .shortfallWon(item.shortfallWon())
        .basisNote(item.basisNote());

    // 추정 불가 매체는 범위가 없으므로 하한/상한을 비워 둔다
    CountRangeResponse impressions = item.estImpressions();
    if (impressions != null) {
      builder.estImpressionsMin(impressions.min()).estImpressionsMax(impressions.max());
    }
    CountRangeResponse clicks = item.estClicks();
    if (clicks != null) {
      builder.estClicksMin(clicks.min()).estClicksMax(clicks.max());
    }

    return builder.build();
  }

  /**
   * 매체의 대표 상품 후보와 그 상품의 대표 단가
   */
  private record Candidate(UUID productId, EstimationProduct product, EstimationPricing pricing,
                           boolean estimatesImpressions) {
  }
}
