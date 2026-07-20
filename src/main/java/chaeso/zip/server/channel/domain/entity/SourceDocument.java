package chaeso.zip.server.channel.domain.entity;

import chaeso.zip.server.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채널/상품/단가가 추출된 원본 문서 이력
 */
@Getter
@Entity
@Table(name = "source_documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceDocument extends BaseEntity {

  @Column(name = "source_path", nullable = false, length = 1000)
  private String sourcePath;

  @Column(name = "file_name", nullable = false, length = 500)
  private String fileName;

  @Column(name = "company_name")
  private String companyName;

  @Column(name = "service_folder")
  private String serviceFolder;

  @Column(name = "file_size_bytes")
  private Long fileSizeBytes;

  @Column(name = "source_format", nullable = false, length = 20)
  private String sourceFormat;

  @Column(name = "extract_method", length = 20)
  private String extractMethod;

  @Column(name = "page_count")
  private Integer pageCount;

  @Column(name = "char_count")
  private Integer charCount;

  @Column(name = "is_image_pdf")
  private Boolean imagePdf;

  @Column(name = "is_media_kit", nullable = false)
  private boolean mediaKit;

  @Column(length = 20)
  private String priority;

  @Column(name = "valid_period", length = 255)
  private String validPeriod;

  @Column(name = "data_quality", length = 20)
  private String dataQuality;

  @Column(name = "extracted_at")
  private LocalDateTime extractedAt;
}
