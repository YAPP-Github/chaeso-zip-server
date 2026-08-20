package chaeso.zip.server.simulation.application;

import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.estimation.application.DefaultCtrProvider;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.estimation.domain.EstimationService;
import chaeso.zip.server.estimation.domain.RepresentativeProduct;
import chaeso.zip.server.estimation.domain.vo.EstimationResult;
import chaeso.zip.server.estimation.domain.vo.PeriodDaysPolicy;
import chaeso.zip.server.simulation.application.dto.AllocationCommand;
import chaeso.zip.server.simulation.application.dto.SimulationCommand;
import chaeso.zip.server.simulation.application.dto.SimulationItemResponse;
import chaeso.zip.server.simulation.application.dto.SimulationResponse;
import chaeso.zip.server.simulation.application.dto.SimulationSummaryResponse;
import chaeso.zip.server.simulation.domain.SimulationNotFoundException;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulation;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulationItem;
import chaeso.zip.server.simulation.domain.repository.BudgetSimulationItemRepository;
import chaeso.zip.server.simulation.domain.repository.BudgetSimulationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        .serviceName(command.serviceName())
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

  @Override
  public Page<SimulationSummaryResponse> findMySimulations(UUID userId, Pageable pageable) {
    Page<BudgetSimulation> simulations =
        budgetSimulationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);

    Map<UUID, List<BudgetSimulationItem>> itemsBySimulation = itemsOf(
        simulations.getContent().stream().map(BudgetSimulation::getId).toList());
    Map<UUID, String> channelNames = channelNames(itemsBySimulation.values().stream()
        .flatMap(List::stream)
        .map(BudgetSimulationItem::getChannelId)
        .distinct()
        .toList());

    return simulations.map(simulation -> SimulationSummaryResponse.from(simulation,
        itemsBySimulation.getOrDefault(simulation.getId(), List.of()), channelNames));
  }

  @Override
  public SimulationResponse findSimulation(UUID userId, UUID simulationId) {
    // 남의 것을 조회했을 때도 없는 것과 같은 404로 응답해 그 id 가 존재한다는 사실을 알려주지 않는다
    return budgetSimulationRepository.findById(simulationId)
        .filter(simulation -> simulation.getUserId().equals(userId))
        .map(this::restore)
        .orElseThrow(() -> new SimulationNotFoundException(simulationId));
  }

  private Map<UUID, List<BudgetSimulationItem>> itemsOf(List<UUID> simulationIds) {
    if (simulationIds.isEmpty()) {
      return Map.of();
    }
    return budgetSimulationItemRepository
        .findByBudgetSimulationIdInOrderBySortOrderAsc(simulationIds).stream()
        .collect(Collectors.groupingBy(BudgetSimulationItem::getBudgetSimulationId));
  }

  private SimulationResponse restore(BudgetSimulation simulation) {
    List<BudgetSimulationItem> items = budgetSimulationItemRepository
        .findByBudgetSimulationIdOrderBySortOrderAsc(simulation.getId());
    Map<UUID, Channel> channels = loadChannelsTolerant(items.stream()
        .map(BudgetSimulationItem::getChannelId)
        .distinct()
        .toList());

    return SimulationResponse.from(simulation, items.stream()
        .map(item -> {
          Channel ch = channels.get(item.getChannelId());
          return SimulationItemResponse.from(item,
              ch != null ? ch.getName() : null,
              ch != null ? ch.getIconUrl() : null);
        })
        .toList());
  }

  private SimulationResponse calculate(SimulationCommand command) {
    int periodDays = PeriodDaysPolicy.daysOf(command.period());
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
        .map(allocation -> {
          Channel channel = channels.get(allocation.channelId());
          return evaluate(
              allocation,
              channel != null ? channel.getName() : null,
              channel != null ? channel.getIconUrl() : null,
              productsByChannel.getOrDefault(allocation.channelId(), List.of()),
              pricingsByProduct,
              periodDays,
              defaultCtrPercent);
        })
        .toList();

    return SimulationResponse.of(
        command.totalBudgetWon(),
        command.period(),
        total(items, SimulationItemResponse::estImpressions),
        total(items, SimulationItemResponse::estClicks),
        items);
  }

  private SimulationItemResponse evaluate(AllocationCommand allocation, String channelName,
      String iconUrl, List<ChannelProduct> products,
      Map<UUID, List<ChannelPricing>> pricingsByProduct,
      int periodDays, BigDecimal defaultCtrPercent) {
    UUID channelId = allocation.channelId();

    RepresentativeProduct representative = RepresentativeProduct
        .select(products, pricingsByProduct, defaultCtrPercent)
        .orElse(null);

    if (representative == null) {
      return SimulationItemResponse.quoteRequired(channelId, channelName, iconUrl, allocation.budgetWon(),
          allocation.allocationPct());
    }

    Long minBudgetWon = minBudgetWonOf(products, representative.productId());
    BigDecimal requiredWon = requiredWon(minBudgetWon, representative.pricing().value());

    if (allocation.budgetWon() <= 0) {
      return SimulationItemResponse.notAllocated(channelId, channelName, iconUrl, representative.productId(),
          allocation.allocationPct(), representative.pricing(), minBudgetWon,
          shortfallWon(requiredWon, 0));
    }

    EstimationResult result = EstimationService.estimate(representative.product(),
        allocation.budgetWon(), periodDays);
    if (result == null) {
      return SimulationItemResponse.quoteRequired(channelId, channelName, iconUrl, allocation.budgetWon(),
          allocation.allocationPct());
    }

    boolean executable = BigDecimal.valueOf(allocation.budgetWon()).compareTo(requiredWon) >= 0;
    Long shortfall = executable ? null : shortfallWon(requiredWon, allocation.budgetWon());
    return SimulationItemResponse.estimated(channelId, channelName, iconUrl, representative.productId(),
        allocation.budgetWon(), allocation.allocationPct(), representative.pricing(), result,
        minBudgetWon, executable, shortfall);
  }

  /** 대표 상품에 등록된 최소 집행 금액(원). 등록돼 있지 않으면 {@code null} */
  private static Long minBudgetWonOf(List<ChannelProduct> products, UUID representativeId) {
    return products.stream()
        .filter(product -> representativeId.equals(product.getId()))
        .findFirst()
        .map(ChannelProduct::getMinBudgetWon)
        .map(Integer::longValue)
        .orElse(null);
  }

  /**
   * 집행 가능 판정의 기준 금액
   */
  private static BigDecimal requiredWon(Long minBudgetWon, BigDecimal price) {
    return minBudgetWon == null ? price : BigDecimal.valueOf(minBudgetWon);
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
    if (channelIds.isEmpty()) {
      return Map.of();
    }
    return channelRepository.findAllById(channelIds).stream()
        .collect(Collectors.toMap(Channel::getId, Channel::getName));
  }

  /** 삭제된 채널 참조 시 예외 대신 결과에서 제외. 복원 전용 */
  private Map<UUID, Channel> loadChannelsTolerant(List<UUID> channelIds) {
    if (channelIds.isEmpty()) {
      return Map.of();
    }
    return channelRepository.findAllById(channelIds).stream()
        .collect(Collectors.toMap(Channel::getId, Function.identity()));
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
  private static Long shortfallWon(BigDecimal requiredWon, long budgetWon) {
    return requiredWon.subtract(BigDecimal.valueOf(budgetWon))
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
        .minBudgetWon(item.minBudgetWon())
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
}
