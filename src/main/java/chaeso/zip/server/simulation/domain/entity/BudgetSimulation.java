package chaeso.zip.server.simulation.domain.entity;

import chaeso.zip.server.simulation.domain.vo.BudgetBasis;
import chaeso.zip.server.simulation.domain.vo.SimPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(name = "budget_simulations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BudgetSimulation {

  @Id
  @UuidGenerator
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "total_budget_won", nullable = false)
  private long totalBudgetWon;

  @Enumerated(EnumType.STRING)
  @Column(name = "budget_basis", nullable = false, length = 20)
  private BudgetBasis budgetBasis;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SimPeriod period;

  @Column(name = "total_est_impressions", nullable = false)
  private long totalEstImpressions;

  @Column(name = "total_est_clicks", nullable = false)
  private long totalEstClicks;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  private void prePersist() {
    this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  @Builder
  private BudgetSimulation(UUID userId, long totalBudgetWon, SimPeriod period,
      long totalEstImpressions, long totalEstClicks) {
    if (userId == null) {
      throw new IllegalArgumentException("BudgetSimulation requires a userId.");
    }
    if (period == null) {
      throw new IllegalArgumentException("BudgetSimulation requires a period.");
    }
    this.userId = userId;
    this.totalBudgetWon = totalBudgetWon;
    this.budgetBasis = BudgetBasis.TOTAL;
    this.period = period;
    this.totalEstImpressions = totalEstImpressions;
    this.totalEstClicks = totalEstClicks;
  }
}
