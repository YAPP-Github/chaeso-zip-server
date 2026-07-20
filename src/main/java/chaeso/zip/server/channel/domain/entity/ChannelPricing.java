package chaeso.zip.server.channel.domain.entity;

import chaeso.zip.server.channel.domain.vo.CurrencyType;
import chaeso.zip.server.channel.domain.vo.PriceType;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.channel.domain.vo.Vat;
import chaeso.zip.server.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품의 단가(상품 1:N 가격)
 */
@Getter
@Entity
@Table(name = "channel_pricing")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelPricing extends BaseEntity {

  @Column(name = "channel_product_id", nullable = false)
  private UUID channelProductId;

  @Enumerated(EnumType.STRING)
  @Column(name = "pricing_model", nullable = false, length = 20)
  private PricingModel pricingModel;

  private BigDecimal value;

  @Column(name = "value_max")
  private BigDecimal valueMax;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CurrencyType currency;

  @Column(name = "unit_period", length = 50)
  private String unitPeriod;

  @Column(name = "unit_days")
  private BigDecimal unitDays;

  @Enumerated(EnumType.STRING)
  @Column(name = "price_type", nullable = false, length = 20)
  private PriceType priceType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Vat vat;

  @Column(length = 255)
  private String segment;

  @Column(name = "valid_period", length = 255)
  private String validPeriod;

  @Column(name = "raw_text")
  private String rawText;

  @Column(name = "source_document_id")
  private UUID sourceDocumentId;

  @Column(nullable = false)
  private boolean verified;
}
