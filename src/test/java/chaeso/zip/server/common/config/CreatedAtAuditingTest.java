package chaeso.zip.server.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.auth.domain.AuthIdentity;
import chaeso.zip.server.auth.domain.AuthIdentityRepository;
import chaeso.zip.server.onboarding.domain.entity.OnboardingAdHistorySnapshot;
import chaeso.zip.server.onboarding.domain.repository.OnboardingAdHistorySnapshotRepository;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.performance.domain.entity.AdPerformance;
import chaeso.zip.server.performance.domain.repository.AdPerformanceRepository;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulation;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulationItem;
import chaeso.zip.server.simulation.domain.repository.BudgetSimulationItemRepository;
import chaeso.zip.server.simulation.domain.repository.BudgetSimulationRepository;
import chaeso.zip.server.support.AdPerformanceFixture;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * {@code updated_at} 이 없어 {@code BaseTimeEntity} 를 상속하지 않는 엔티티들의 {@code created_at}
 * 이 감사(auditing)로 채워지는지 검증한다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class CreatedAtAuditingTest {

  @Autowired
  private AuthIdentityRepository authIdentityRepository;
  @Autowired
  private AdPerformanceRepository adPerformanceRepository;
  @Autowired
  private OnboardingAdHistorySnapshotRepository onboardingAdHistorySnapshotRepository;
  @Autowired
  private BudgetSimulationRepository budgetSimulationRepository;
  @Autowired
  private BudgetSimulationItemRepository budgetSimulationItemRepository;

  @Test
  @DisplayName("로그인 방식 저장 시 생성 시각이 채워진다")
  void fillsAuthIdentityCreatedAt() {
    AuthIdentity saved =
        authIdentityRepository.save(AuthIdentity.createLocal(UUID.randomUUID(), "hashed"));

    assertUtcNow(saved.getCreatedAt());
  }

  @Test
  @DisplayName("광고 집행 실적 저장 시 생성 시각이 채워진다")
  void fillsAdPerformanceCreatedAt() {
    AdPerformance saved = adPerformanceRepository.save(AdPerformanceFixture.adPerformance());

    assertUtcNow(saved.getCreatedAt());
  }

  @Test
  @DisplayName("온보딩 집행 실적 스냅샷 저장 시 생성 시각이 채워진다")
  void fillsOnboardingAdHistorySnapshotCreatedAt() {
    OnboardingAdHistorySnapshot saved =
        onboardingAdHistorySnapshotRepository.save(OnboardingAdHistorySnapshot.snapshotBuilder()
            .onboardingId(UUID.randomUUID())
            .channelId(UUID.randomUUID())
            .channelNameSnap("11번가 광고")
            .budgetWonSnap(1_000_000L)
            .impressionsSnap(100_000L)
            .clicksSnap(2_000L)
            .conversionsSnap(10L)
            .startedAtSnap(LocalDate.of(2026, Month.JULY, 1))
            .endedAtSnap(LocalDate.of(2026, Month.JULY, 31))
            .build());

    assertUtcNow(saved.getCreatedAt());
  }

  @Test
  @DisplayName("예산 시뮬레이션 저장 시 생성 시각이 채워진다")
  void fillsBudgetSimulationCreatedAt() {
    BudgetSimulation saved = budgetSimulationRepository.save(BudgetSimulation.builder()
        .userId(UUID.randomUUID())
        .totalBudgetWon(3_000_000L)
        .period(CampaignPeriod.M1)
        .totalEstImpressions(1_000_000L)
        .totalEstClicks(25_000L)
        .build());

    assertUtcNow(saved.getCreatedAt());
  }

  @Test
  @DisplayName("예산 시뮬레이션 항목 저장 시 생성 시각이 채워진다")
  void fillsBudgetSimulationItemCreatedAt() {
    BudgetSimulationItem saved = budgetSimulationItemRepository.save(BudgetSimulationItem.builder()
        .budgetSimulationId(UUID.randomUUID())
        .channelId(UUID.randomUUID())
        .sortOrder(0)
        .allocatedBudgetWon(3_000_000L)
        .executable(true)
        .build());

    assertUtcNow(saved.getCreatedAt());
  }

  /**
   * 생성 시각이 UTC 기준 현재 시각인지 확인한다.
   */
  private static void assertUtcNow(LocalDateTime createdAt) {
    assertThat(createdAt).isNotNull();
    Instant createdAtInstant = createdAt.toInstant(ZoneOffset.UTC);
    assertThat(Duration.between(Instant.now(), createdAtInstant).abs())
        .isLessThan(Duration.ofSeconds(30));
  }
}
