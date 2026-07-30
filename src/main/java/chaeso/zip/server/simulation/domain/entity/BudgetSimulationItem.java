package chaeso.zip.server.simulation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 저장된 예산 시뮬레이션의 매체별 결과 스냅샷
 */
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "budget_simulation_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BudgetSimulationItem {

  @Id
  @UuidGenerator
  private UUID id;

  @Column(name = "budget_simulation_id", nullable = false)
  private UUID budgetSimulationId;

  @Column(name = "channel_id", nullable = false)
  private UUID channelId;

  /** 대표로 선택된 상품. 단가 정보가 있는 상품이 없으면 null */
  @Column(name = "channel_product_id")
  private UUID channelProductId;

  /** 매체 나열 순서 */
  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "allocated_budget_won", nullable = false)
  private long allocatedBudgetWon;

  @Column(name = "allocation_pct")
  private BigDecimal allocationPct;

  @Column(name = "est_impressions_min")
  private Long estImpressionsMin;

  @Column(name = "est_impressions_max")
  private Long estImpressionsMax;

  @Column(name = "est_clicks_min")
  private Long estClicksMin;

  @Column(name = "est_clicks_max")
  private Long estClicksMax;

  @Column(name = "cpc_won")
  private BigDecimal cpcWon;

  @Column(name = "cpm_won")
  private BigDecimal cpmWon;

  @Column(name = "is_executable", nullable = false)
  private boolean executable;

  /** 집행 불가일 때 부족한 금액(원) */
  @Column(name = "shortfall_won")
  private Long shortfallWon;

  @Column(name = "basis_note", length = 500)
  private String basisNote;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private BudgetSimulationItem(UUID budgetSimulationId, UUID channelId, UUID channelProductId,
      int sortOrder, long allocatedBudgetWon, BigDecimal allocationPct, Long estImpressionsMin,
      Long estImpressionsMax, Long estClicksMin, Long estClicksMax, BigDecimal cpcWon,
      BigDecimal cpmWon, boolean executable, Long shortfallWon, String basisNote) {
    if (budgetSimulationId == null) {
      throw new IllegalArgumentException("BudgetSimulationItem requires a budgetSimulationId.");
    }
    if (channelId == null) {
      throw new IllegalArgumentException("BudgetSimulationItem requires a channelId.");
    }
    this.budgetSimulationId = budgetSimulationId;
    this.channelId = channelId;
    this.channelProductId = channelProductId;
    this.sortOrder = sortOrder;
    this.allocatedBudgetWon = allocatedBudgetWon;
    this.allocationPct = allocationPct;
    this.estImpressionsMin = estImpressionsMin;
    this.estImpressionsMax = estImpressionsMax;
    this.estClicksMin = estClicksMin;
    this.estClicksMax = estClicksMax;
    this.cpcWon = cpcWon;
    this.cpmWon = cpmWon;
    this.executable = executable;
    this.shortfallWon = shortfallWon;
    this.basisNote = basisNote;
  }
}
