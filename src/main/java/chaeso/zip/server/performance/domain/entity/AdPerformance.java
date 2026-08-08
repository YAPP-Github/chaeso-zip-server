package chaeso.zip.server.performance.domain.entity;

import chaeso.zip.server.performance.domain.vo.PerfSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * 사용자 소유의 광고 집행 실적.
 */
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "ad_performances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdPerformance {

  @Id
  @UuidGenerator
  private UUID id;

  @Column(name = "user_id")
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 20)
  private PerfSource sourceType;

  @Column(name = "channel_id")
  private UUID channelId;

  @Column(name = "external_channel_name")
  private String externalChannelName;

  @Column(name = "started_at")
  private LocalDate startedAt;

  @Column(name = "ended_at")
  private LocalDate endedAt;

  @Column(name = "budget_won")
  private Long budgetWon;

  private Long impressions;

  private Long clicks;

  private Long conversions;

  @Column(name = "ctr_actual")
  private BigDecimal ctrActual;

  @Column(name = "cpc_actual")
  private BigDecimal cpcActual;

  @Column(name = "cpa_actual")
  private BigDecimal cpaActual;

  @Column(name = "raw_file_url")
  private String rawFileUrl;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private AdPerformance(UUID userId, PerfSource sourceType, UUID channelId,
      String externalChannelName, Long budgetWon, Long impressions, Long clicks, Long conversions,
      LocalDate startedAt, LocalDate endedAt, String rawFileUrl) {
    if (channelId == null && externalChannelName == null) {
      throw new IllegalArgumentException(
          "AdPerformance requires either channelId or externalChannelName.");
    }
    this.userId = userId;
    this.sourceType = sourceType;
    this.channelId = channelId;
    this.externalChannelName = externalChannelName;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
    this.budgetWon = budgetWon;
    this.impressions = impressions;
    this.clicks = clicks;
    this.conversions = conversions;
    this.rawFileUrl = rawFileUrl;
    this.ctrActual = ratio(clicks, impressions);
    this.cpcActual = ratio(budgetWon, clicks);
    this.cpaActual = ratio(budgetWon, conversions);
  }

  private static BigDecimal ratio(Long numerator, Long denominator) {
    if (numerator == null || denominator == null || denominator == 0L) {
      return null;
    }
    return BigDecimal.valueOf(numerator)
        .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
  }
}
