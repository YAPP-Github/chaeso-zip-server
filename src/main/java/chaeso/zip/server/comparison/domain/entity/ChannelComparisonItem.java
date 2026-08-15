package chaeso.zip.server.comparison.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "channel_comparison_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelComparisonItem {

  @Id
  @UuidGenerator
  private UUID id;

  @Column(name = "comparison_id", nullable = false)
  private UUID comparisonId;

  @Column(name = "channel_id", nullable = false)
  private UUID channelId;

  /** 온보딩 있으면 적합도순, 없으면 요청 순서. 1부터 시작한다 */
  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  /** 적합도(%) */
  @Column(name = "match_rate")
  private Integer matchRate;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "tags_snap")
  private List<String> tagsSnap;

  @Column(name = "channel_name", nullable = false)
  private String channelName;

  @Column(name = "preview_image_url_snap", length = 500)
  private String previewImageUrlSnap;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "display_platforms_snap")
  private List<String> displayPlatformsSnap;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "advantages_snap")
  private List<String> advantagesSnap;

  @Column(name = "audience_summary_snap")
  private String audienceSummarySnap;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "ad_formats_snap")
  private List<String> adFormatsSnap;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "targeting_methods_snap")
  private List<String> targetingMethodsSnap;

  @Column(name = "execution_type_snap", length = 20)
  private String executionTypeSnap;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "pricing_models_all")
  private List<String> pricingModelsAll;

  @Column(name = "cpc_won")
  private BigDecimal cpcWon;

  @Column(name = "cpm_won")
  private BigDecimal cpmWon;

  @Column(name = "min_budget_won_snap")
  private Integer minBudgetWonSnap;

  @Column(name = "est_impressions_min")
  private Long estImpressionsMin;

  @Column(name = "est_impressions_max")
  private Long estImpressionsMax;

  @Column(name = "est_clicks_min")
  private Long estClicksMin;

  @Column(name = "est_clicks_max")
  private Long estClicksMax;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private ChannelComparisonItem(UUID comparisonId, UUID channelId, int sortOrder,
      Integer matchRate, List<String> tagsSnap, String channelName, String previewImageUrlSnap,
      List<String> displayPlatformsSnap, List<String> advantagesSnap, String audienceSummarySnap,
      List<String> adFormatsSnap, List<String> targetingMethodsSnap, String executionTypeSnap,
      List<String> pricingModelsAll, BigDecimal cpcWon, BigDecimal cpmWon,
      Integer minBudgetWonSnap, Long estImpressionsMin, Long estImpressionsMax, Long estClicksMin,
      Long estClicksMax) {
    if (comparisonId == null) {
      throw new IllegalArgumentException("ChannelComparisonItem requires a comparisonId.");
    }
    if (channelId == null) {
      throw new IllegalArgumentException("ChannelComparisonItem requires a channelId.");
    }
    this.comparisonId = comparisonId;
    this.channelId = channelId;
    this.sortOrder = sortOrder;
    this.matchRate = matchRate;
    this.tagsSnap = tagsSnap;
    this.channelName = channelName;
    this.previewImageUrlSnap = previewImageUrlSnap;
    this.displayPlatformsSnap = displayPlatformsSnap;
    this.advantagesSnap = advantagesSnap;
    this.audienceSummarySnap = audienceSummarySnap;
    this.adFormatsSnap = adFormatsSnap;
    this.targetingMethodsSnap = targetingMethodsSnap;
    this.executionTypeSnap = executionTypeSnap;
    this.pricingModelsAll = pricingModelsAll;
    this.cpcWon = cpcWon;
    this.cpmWon = cpmWon;
    this.minBudgetWonSnap = minBudgetWonSnap;
    this.estImpressionsMin = estImpressionsMin;
    this.estImpressionsMax = estImpressionsMax;
    this.estClicksMin = estClicksMin;
    this.estClicksMax = estClicksMax;
  }
}
