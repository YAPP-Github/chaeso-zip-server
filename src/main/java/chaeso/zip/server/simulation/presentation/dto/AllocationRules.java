package chaeso.zip.server.simulation.presentation.dto;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class AllocationRules {

  private AllocationRules() {
  }

  /** 한 채널에 예산을 두 번 배분하지 않았는지 */
  static boolean distinctChannels(List<AllocationRequest> allocations) {
    if (allocations == null) {
      return true;
    }
    List<UUID> channelIds = allocations.stream()
        .map(AllocationRequest::channelId)
        .filter(Objects::nonNull)
        .toList();
    return channelIds.size() == Set.copyOf(channelIds).size();
  }

  /** 배분한 예산의 합이 총 예산을 넘지 않는지 */
  static boolean withinTotalBudget(List<AllocationRequest> allocations, Integer totalBudgetWon) {
    if (allocations == null || totalBudgetWon == null) {
      return true;
    }
    long allocated = allocations.stream()
        .map(AllocationRequest::budgetWon)
        .filter(Objects::nonNull)
        .mapToLong(Integer::longValue)
        .sum();
    return allocated <= totalBudgetWon;
  }
}
