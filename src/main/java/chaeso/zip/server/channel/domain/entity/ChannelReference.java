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
 * 채널(또는 상품)의 집행 사례/벤치마크
 */
@Getter
@Entity
@Table(name = "channel_references")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelReference {

  @Id
  @UuidGenerator
  private UUID id;

  @Column(name = "channel_id")
  private UUID channelId;

  @Column(name = "channel_product_id")
  private UUID channelProductId;

  @Column(name = "result_text")
  private String resultText;

  @Column(name = "is_benchmark", nullable = false)
  private boolean benchmark;

  @Column(name = "source_document_id")
  private UUID sourceDocumentId;

  @Column(nullable = false)
  private boolean verified;
}
