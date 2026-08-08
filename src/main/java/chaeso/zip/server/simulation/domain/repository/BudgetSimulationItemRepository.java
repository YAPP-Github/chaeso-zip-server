package chaeso.zip.server.simulation.domain.repository;

import chaeso.zip.server.simulation.domain.entity.BudgetSimulationItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetSimulationItemRepository
    extends JpaRepository<BudgetSimulationItem, UUID> {

  List<BudgetSimulationItem> findByBudgetSimulationIdOrderBySortOrderAsc(UUID budgetSimulationId);

  List<BudgetSimulationItem> findByBudgetSimulationIdInOrderBySortOrderAsc(
      Collection<UUID> budgetSimulationIds);
}
