package chaeso.zip.server.channel.domain.entity;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.ExecutionType;
import chaeso.zip.server.channel.domain.vo.Gender;
import chaeso.zip.server.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "channels")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel extends BaseEntity {

  @Column(nullable = false)
  private String name;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "display_platforms")
  private List<String> displayPlatforms;

  @Column(name = "logo_url", length = 500)
  private String logoUrl;

  @Column(name = "media_type", length = 20)
  private String mediaType;

  @Enumerated(EnumType.STRING)
  @Column(name = "primary_category", length = 30)
  private Category primaryCategory;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "suitable_categories")
  private List<Category> suitableCategories;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "default_tags")
  private List<String> defaultTags;

  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> advantages;

  @Column(name = "preview_image_url", length = 500)
  private String previewImageUrl;

  @Column(name = "audience_summary")
  private String audienceSummary;

  @Column(name = "primary_age_band", length = 50)
  private String primaryAgeBand;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "age_band_codes")
  private List<AgeBand> ageBandCodes;

  @Enumerated(EnumType.STRING)
  @Column(name = "primary_gender", length = 20)
  private Gender primaryGender;

  @Column(name = "audience_traits")
  private String audienceTraits;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "ad_formats")
  private List<String> adFormats;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "targeting_methods")
  private List<String> targetingMethods;

  @Enumerated(EnumType.STRING)
  @Column(name = "execution_type", length = 20)
  private ExecutionType executionType;

  @Column(name = "min_budget_won")
  private Integer minBudgetWon;

  @Column(name = "max_budget_won")
  private Integer maxBudgetWon;

  @Column(name = "avg_daily_impressions")
  private Long avgDailyImpressions;

  private String description;

  @Column(name = "recommendation_basis")
  private String recommendationBasis;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "source_document_id")
  private UUID sourceDocumentId;
}
