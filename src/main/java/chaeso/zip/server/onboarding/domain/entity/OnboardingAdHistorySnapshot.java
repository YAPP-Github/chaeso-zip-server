package chaeso.zip.server.onboarding.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
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
 * 온보딩 제출 시점의 집행 실적 스냅샷.
 */
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "onboarding_ad_history_snapshots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnboardingAdHistorySnapshot {

  @Id
  @UuidGenerator
  private UUID id;

  @Column(name = "onboarding_id", nullable = false)
  private UUID onboardingId;

  @Column(name = "channel_id")
  private UUID channelId;

  @Column(name = "channel_name_snap", nullable = false)
  private String channelNameSnap;

  @Column(name = "budget_won_snap")
  private Long budgetWonSnap;

  @Column(name = "impressions_snap")
  private Long impressionsSnap;

  @Column(name = "clicks_snap")
  private Long clicksSnap;

  @Column(name = "conversions_snap")
  private Long conversionsSnap;

  @Column(name = "started_at_snap")
  private LocalDate startedAtSnap;

  @Column(name = "ended_at_snap")
  private LocalDate endedAtSnap;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder(builderMethodName = "snapshotBuilder")
  private OnboardingAdHistorySnapshot(UUID onboardingId, UUID channelId, String channelNameSnap,
      Long budgetWonSnap, Long impressionsSnap, Long clicksSnap, Long conversionsSnap,
      LocalDate startedAtSnap, LocalDate endedAtSnap) {
    if (channelNameSnap == null || channelNameSnap.isBlank()) {
      throw new IllegalArgumentException(
          "OnboardingAdHistorySnapshot requires a non-blank channelNameSnap.");
    }
    this.onboardingId = onboardingId;
    this.channelId = channelId;
    this.channelNameSnap = channelNameSnap;
    this.budgetWonSnap = budgetWonSnap;
    this.impressionsSnap = impressionsSnap;
    this.clicksSnap = clicksSnap;
    this.conversionsSnap = conversionsSnap;
    this.startedAtSnap = startedAtSnap;
    this.endedAtSnap = endedAtSnap;
  }
}
