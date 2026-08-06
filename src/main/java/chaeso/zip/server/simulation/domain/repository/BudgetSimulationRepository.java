package chaeso.zip.server.simulation.domain.repository;

import chaeso.zip.server.simulation.domain.entity.BudgetSimulation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetSimulationRepository extends JpaRepository<BudgetSimulation, UUID> {

  Optional<BudgetSimulation> findFirstByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

  Page<BudgetSimulation> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);
}
