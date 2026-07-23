package chaeso.zip.server.onboarding.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ObjectivePolicyTest {

  @Test
  @DisplayName("서비스 형태별 허용 광고 목표 규칙을 검증한다")
  void validatesAllowedObjectives() {
    assertThat(ObjectivePolicy.allowedFor(ServiceType.WEB))
        .containsExactlyInAnyOrder(
            CampaignObjective.AWARENESS,
            CampaignObjective.VIDEO_VIEW,
            CampaignObjective.TRAFFIC,
            CampaignObjective.LEAD,
            CampaignObjective.CONVERSION);
    assertThat(ObjectivePolicy.allows(ServiceType.WEB, CampaignObjective.APP_INSTALL)).isFalse();

    assertThat(ObjectivePolicy.allowedFor(ServiceType.MOBILE_APP))
        .contains(CampaignObjective.APP_INSTALL, CampaignObjective.IN_APP_ACTION)
        .hasSize(7);
  }

  @ParameterizedTest
  @EnumSource(value = ServiceType.class, names = {"WEB_AND_APP", "OTHER"})
  @DisplayName("통합형 및 기타 서비스는 모든 목표를 허용한다")
  void combinedTypesAllowEverything(ServiceType serviceType) {
    assertThat(ObjectivePolicy.allowedFor(serviceType))
        .containsExactlyInAnyOrder(CampaignObjective.values());
  }
}
