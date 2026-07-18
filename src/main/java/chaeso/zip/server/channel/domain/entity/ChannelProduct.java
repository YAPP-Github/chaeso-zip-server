package chaeso.zip.server.channel.domain.entity;

import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 채널의 광고 상품(채널 1:N 상품)
 */
@Getter
@Entity
@Table(name = "channel_products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelProduct extends BaseEntity {

  @Column(name = "channel_id", nullable = false)
  private UUID channelId;

  @Column(name = "product_name")
  private String productName;

  @Column(name = "inventory_type", length = 100)
  private String inventoryType;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "supported_objectives")
  private List<CampaignObjective> supportedObjectives;

  @Column(name = "min_budget_won")
  private Integer minBudgetWon;

  @Column(name = "max_budget_won")
  private Integer maxBudgetWon;

  private BigDecimal ctr;

  @Column(name = "ctr_min")
  private BigDecimal ctrMin;

  @Column(name = "ctr_max")
  private BigDecimal ctrMax;

  @Column(name = "expected_impressions")
  private Long expectedImpressions;

  @Column(name = "expected_period", length = 50)
  private String expectedPeriod;

  @Column(name = "source_document_id")
  private UUID sourceDocumentId;
}
