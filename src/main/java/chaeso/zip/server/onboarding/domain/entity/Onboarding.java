package chaeso.zip.server.onboarding.domain.entity;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.common.entity.BaseEntity;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.BudgetRange;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ObjectivePolicy;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 온보딩 응답 애그리거트 루트.
 */
@Getter
@Entity
@Table(name = "onboarding_responses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Onboarding extends BaseEntity {

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "service_name")
  private String serviceName;

  @Enumerated(EnumType.STRING)
  @Column(length = 30)
  private Category industry;

  @Enumerated(EnumType.STRING)
  @Column(name = "service_type", length = 20)
  private ServiceType serviceType;

  @Enumerated(EnumType.STRING)
  @Column(name = "campaign_objective", length = 20)
  private CampaignObjective campaignObjective;

  @Embedded
  private BudgetRange budgetRange;

  @Enumerated(EnumType.STRING)
  @Column(name = "period", nullable = false, length = 20)
  private CampaignPeriod period;

  @Enumerated(EnumType.STRING)
  @Column(name = "ad_experience", length = 20)
  private AdExperience adExperience;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "target_age_bands")
  private List<AgeBand> targetAgeBands;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "raw_file_urls")
  private List<String> rawFileUrls;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  private Onboarding(UUID userId, String serviceName, Category industry,
      ServiceType serviceType, List<AgeBand> targetAgeBands, CampaignObjective campaignObjective,
      BudgetRange budgetRange, CampaignPeriod period, AdExperience adExperience,
      List<String> rawFileUrls) {
    this.userId = userId;
    this.serviceName = serviceName;
    this.industry = industry;
    this.serviceType = serviceType;
    this.targetAgeBands = targetAgeBands;
    this.campaignObjective = campaignObjective;
    this.budgetRange = budgetRange;
    this.period = period;
    this.adExperience = adExperience;
    this.rawFileUrls = rawFileUrls;
    this.isActive = true;
  }

  @Builder(builderMethodName = "createBuilder")
  public static Onboarding create(UUID userId, String serviceName, Category industry,
      ServiceType serviceType, List<AgeBand> targetAgeBands, CampaignObjective campaignObjective,
      BudgetRange budgetRange, CampaignPeriod period, AdExperience adExperience,
      List<String> rawFileUrls) {
    validateTagRules(serviceType, campaignObjective, targetAgeBands, period, budgetRange);
    return new Onboarding(userId, serviceName, industry, serviceType, targetAgeBands,
        campaignObjective, budgetRange, period, adExperience, rawFileUrls);
  }

  /** 최신 온보딩 태그 수정을 위한 메서드 */
  public void updateTags(Category industry, ServiceType serviceType,
      List<AgeBand> targetAgeBands, CampaignObjective campaignObjective,
      BudgetRange budgetRange, CampaignPeriod period) {
    validateTagRules(serviceType, campaignObjective, targetAgeBands, period, budgetRange);
    this.industry = industry;
    this.serviceType = serviceType;
    this.targetAgeBands = targetAgeBands;
    this.campaignObjective = campaignObjective;
    this.budgetRange = budgetRange;
    this.period = period;
  }

  /** 최소 예산을 반환한다. */
  public Long getBudgetMin() {
    return budgetRange.getBudgetMin();
  }

  /** 최대 예산을 반환한다. */
  public Long getBudgetMax() {
    return budgetRange.getBudgetMax();
  }

  /** 온보딩 태그 속성들의 도메인 규칙을 일괄 검증한다. */
  private static void validateTagRules(ServiceType serviceType, CampaignObjective campaignObjective,
      List<AgeBand> targetAgeBands, CampaignPeriod period, BudgetRange budgetRange) {
    validateObjectivePolicy(serviceType, campaignObjective);
    validateTargetAgeBands(targetAgeBands);
    validatePeriod(period);
    validateBudgetRange(budgetRange);
  }

  /** 예산 범위 존재 여부를 검증한다. */
  private static void validateBudgetRange(BudgetRange budgetRange) {
    if (budgetRange == null) {
      throw new OnboardingBusinessException(OnboardingErrorCode.INVALID_BUDGET_RANGE);
    }
  }

  /** 집행 기간 필수 여부를 검증한다. */
  private static void validatePeriod(CampaignPeriod period) {
    if (period == null) {
      throw new OnboardingBusinessException(OnboardingErrorCode.PERIOD_REQUIRED);
    }
  }

  /** 서비스 형태별 허용되는 광고 목표 정책을 검증한다. */
  private static void validateObjectivePolicy(ServiceType serviceType,
      CampaignObjective campaignObjective) {
    if (!ObjectivePolicy.allows(serviceType, campaignObjective)) {
      throw new OnboardingBusinessException(OnboardingErrorCode.OBJECTIVE_NOT_ALLOWED);
    }
  }

  /** 타깃 연령대 선택 정책을 검증한다 ('잘 모르겠어요'는 단독 선택만 허용). */
  private static void validateTargetAgeBands(List<AgeBand> targetAgeBands) {
    if (targetAgeBands != null && targetAgeBands.contains(AgeBand.UNDECIDED)
        && targetAgeBands.size() > 1) {
      throw new OnboardingBusinessException(OnboardingErrorCode.INVALID_AGE_BAND_SELECTION);
    }
  }

  /** 새 온보딩이 제출되면 이전 응답을 비활성으로 내린다. */
  public void deactivate() {
    this.isActive = false;
  }
}
