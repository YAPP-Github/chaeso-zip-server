package chaeso.zip.server.channel.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 채널(또는 상품) 단위 오디언스 규모 지표(MAU/DAU 등)
 */
@Getter
@Entity
@Table(name = "channel_audience_metrics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelAudienceMetric {

  @Id
  @UuidGenerator
  private UUID id;

  @Column(name = "channel_id")
  private UUID channelId;

  @Column(name = "channel_product_id")
  private UUID channelProductId;

  @Column(name = "metric_name", nullable = false)
  private String metricName;

  @Column(name = "value_numeric")
  private BigDecimal valueNumeric;

  @Column(name = "value_text")
  private String valueText;

  @Column(length = 50)
  private String unit;

  @Column(length = 50)
  private String period;

  @Column(name = "source_document_id")
  private UUID sourceDocumentId;

  @Column(nullable = false)
  private boolean verified;
}
