package chaeso.zip.server.onboarding.domain.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OnboardingTest {

  @Test
  @DisplayName("최소 예산이 없으면 온보딩을 생성할 수 없다")
  void rejectsNullBudgetMin() {
    Onboarding.OnboardingBuilder builder = validBuilder()
        .budgetMin(null);

    assertThatThrownBy(builder::build)
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.INVALID_BUDGET_RANGE);
  }

  @Test
  @DisplayName("최대 예산이 없으면 온보딩을 생성할 수 없다")
  void rejectsNullBudgetMax() {
    Onboarding.OnboardingBuilder builder = validBuilder()
        .budgetMax(null);

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
        .budgetMin(1_000_000L)
        .budgetMax(3_000_000L)
        .period(CampaignPeriod.M1);
  }
}
