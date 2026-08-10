package chaeso.zip.server.onboarding.application;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.application.dto.OnboardingSubmitResponse;
import chaeso.zip.server.onboarding.application.dto.SubmitOnboardingCommand;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 온보딩 제출이 실제로 저장되는지 검증하는 통합 테스트
 */
@SpringBootTest
class OnboardingServiceSubmitIntegrationTest {

  @Autowired
  private OnboardingService onboardingService;

  @Autowired
  private OnboardingRepository onboardingRepository;

  @AfterEach
  void tearDown() {
    onboardingRepository.deleteAll();
  }

  @Test
  @DisplayName("온보딩을 제출하면 실제로 저장되어 별도 조회로 확인된다")
  void submitActuallyCommits() {
    SubmitOnboardingCommand command = SubmitOnboardingCommand.builder()
        .serviceName("채소집")
        .industry(Category.SHOPPING_COMMERCE)
        .serviceType(ServiceType.WEB)
        .targetAgeBands(List.of())
        .campaignObjective(CampaignObjective.TRAFFIC)
        .budgetMin(1_000_000L)
        .budgetMax(5_000_000L)
        .period(CampaignPeriod.M1)
        .adExperience(AdExperience.NONE)
        .adHistory(List.of())
        .rawFileKeys(List.of())
        .build();

    OnboardingSubmitResponse response = onboardingService.submit(null, command);

    Optional<Onboarding> saved = onboardingRepository.findById(response.onboardingId());
    assertThat(saved).isPresent();
    assertThat(saved.get().isActive()).isTrue();
  }
}
