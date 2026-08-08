package chaeso.zip.server.recommendation.domain.entity;

import chaeso.zip.server.channel.domain.vo.PricingModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "channel_recommendations", uniqueConstraints = {
    @UniqueConstraint(name = "uq_channel_recommendation_onboarding_channel",
        columnNames = {"onboarding_id", "channel_id"}),
    @UniqueConstraint(name = "uq_channel_recommendation_onboarding_rank",
        columnNames = {"onboarding_id", "rank"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelRecommendation {

  @Id
  @UuidGenerator
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "onboarding_id", nullable = false)
  private UUID onboardingId;

  @Column(name = "channel_id", nullable = false)
  private UUID channelId;

  /** 추천 순위. 1 부터 시작한다 */
  @Column(name = "rank", nullable = false)
  private int rank;

  /** 적합도(%) */
  @Column(name = "score", nullable = false)
  private int score;

  @Column(name = "reason", nullable = false, length = 500)
  private String reason;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "reason_tags")
  private List<String> reasonTags;

  @Column(name = "channel_name", nullable = false)
  private String channelName;

  @Enumerated(EnumType.STRING)
  @Column(name = "est_pricing_model", length = 20)
  private PricingModel estPricingModel;

  @Column(name = "est_unit_price")
  private BigDecimal estUnitPrice;

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

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "pricing_models_all")
  private List<String> pricingModelsAll;

  @Column(name = "min_budget_won_snap")
  private Long minBudgetWonSnap;

  /** 추천이 만든 주요 타깃 문구 */
  @Column(name = "audience_summary_snap")
  private String audienceSummarySnap;

  @Column(name = "is_executable", nullable = false)
  private boolean isExecutable;

  @Column(name = "shortfall_won")
  private Long shortfallWon;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private ChannelRecommendation(UUID userId, UUID onboardingId, UUID channelId, int rank, int score,
      String reason, List<String> reasonTags, String channelName, PricingModel estPricingModel,
      BigDecimal estUnitPrice, Long estImpressionsMin, Long estImpressionsMax, Long estClicksMin,
      Long estClicksMax, BigDecimal cpcWon, List<String> pricingModelsAll, Long minBudgetWonSnap,
      String audienceSummarySnap, boolean executable, Long shortfallWon) {
    if (userId == null) {
      throw new IllegalArgumentException("ChannelRecommendation requires a userId.");
    }
    if (onboardingId == null) {
      throw new IllegalArgumentException("ChannelRecommendation requires an onboardingId.");
    }
    if (channelId == null) {
      throw new IllegalArgumentException("ChannelRecommendation requires a channelId.");
    }
    this.userId = userId;
    this.onboardingId = onboardingId;
    this.channelId = channelId;
    this.rank = rank;
    this.score = score;
    this.reason = reason;
    this.reasonTags = reasonTags;
    this.channelName = channelName;
    this.estPricingModel = estPricingModel;
    this.estUnitPrice = estUnitPrice;
    this.estImpressionsMin = estImpressionsMin;
    this.estImpressionsMax = estImpressionsMax;
    this.estClicksMin = estClicksMin;
    this.estClicksMax = estClicksMax;
    this.cpcWon = cpcWon;
    this.pricingModelsAll = pricingModelsAll;
    this.minBudgetWonSnap = minBudgetWonSnap;
    this.audienceSummarySnap = audienceSummarySnap;
    this.isExecutable = executable;
    this.shortfallWon = shortfallWon;
  }
}
