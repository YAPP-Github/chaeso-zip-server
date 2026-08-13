package chaeso.zip.server.onboarding.domain.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.onboarding.domain.vo.BudgetRange;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OnboardingTest {

  @Test
  @DisplayName("예산 범위가 없으면 온보딩을 생성할 수 없다")
  void rejectsNullBudgetRange() {
    Onboarding.OnboardingBuilder builder = validBuilder()
        .budgetRange(null);

    assertThatThrownBy(builder::build)
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.INVALID_BUDGET_RANGE);
  }

  @Test
  @DisplayName("집행 기간이 없으면 온보딩을 생성할 수 없다")
  void rejectsNullPeriod() {
    Onboarding.OnboardingBuilder builder = validBuilder()
        .period(null);

    assertThatThrownBy(builder::build)
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.PERIOD_REQUIRED);
  }

  private static Onboarding.OnboardingBuilder validBuilder() {
    return Onboarding.createBuilder()
        .serviceType(ServiceType.WEB)
        .campaignObjective(CampaignObjective.TRAFFIC)
        .budgetRange(BudgetRange.of(1_000_000L, 3_000_000L))
        .period(CampaignPeriod.M1);
  }
}
