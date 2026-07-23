package chaeso.zip.server.onboarding.domain.entity;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.common.entity.BaseEntity;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ObjectivePolicy;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
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

/**
 * 온보딩 응답 애그리거트 루트.
 */
@Getter
@Entity
@Table(name = "onboarding_responses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Onboarding extends BaseEntity {

  @Column(name = "user_id", nullable = false)
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

  @Column(name = "budget_min")
  private Long budgetMin;

  @Column(name = "budget_max")
  private Long budgetMax;

  @Enumerated(EnumType.STRING)
  @Column(name = "period", length = 20)
  private CampaignPeriod period;

  @Enumerated(EnumType.STRING)
  @Column(name = "ad_experience", length = 20)
  private AdExperience adExperience;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "target_age_bands")
  private List<AgeBand> targetAgeBands;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  private Onboarding(UUID userId, String serviceName, Category industry,
      ServiceType serviceType, List<AgeBand> targetAgeBands, CampaignObjective campaignObjective,
      Long budgetMin, Long budgetMax, CampaignPeriod period, AdExperience adExperience) {
    this.userId = userId;
    this.serviceName = serviceName;
    this.industry = industry;
    this.serviceType = serviceType;
    this.targetAgeBands = targetAgeBands;
    this.campaignObjective = campaignObjective;
    this.budgetMin = budgetMin;
    this.budgetMax = budgetMax;
    this.period = period;
    this.adExperience = adExperience;
    this.isActive = true;
  }

  public static Onboarding create(UUID userId, String serviceName, Category industry,
      ServiceType serviceType, List<AgeBand> targetAgeBands, CampaignObjective campaignObjective,
      Long budgetMin, Long budgetMax, CampaignPeriod period, AdExperience adExperience) {
    if (budgetMin > budgetMax) {
      throw new OnboardingBusinessException(OnboardingErrorCode.INVALID_BUDGET_RANGE);
    }
    if (!ObjectivePolicy.allows(serviceType, campaignObjective)) {
      throw new OnboardingBusinessException(OnboardingErrorCode.OBJECTIVE_NOT_ALLOWED);
    }
    return new Onboarding(userId, serviceName, industry, serviceType, targetAgeBands,
        campaignObjective, budgetMin, budgetMax, period, adExperience);
  }

  /** 새 온보딩이 제출되면 이전 응답을 비활성으로 내린다. */
  public void deactivate() {
    this.isActive = false;
  }
}
