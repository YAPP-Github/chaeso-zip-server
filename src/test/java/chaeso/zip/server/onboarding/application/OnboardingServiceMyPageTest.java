package chaeso.zip.server.onboarding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.application.dto.MyOnboardingTagResponse;
import chaeso.zip.server.onboarding.application.dto.UpdateOnboardingTagCommand;
import chaeso.zip.server.onboarding.domain.OnboardingNotFoundException;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.onboarding.domain.vo.BudgetRange;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import chaeso.zip.server.support.OnboardingFixture;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceMyPageTest {

  @Mock
  private OnboardingRepository onboardingRepository;

  @InjectMocks
  private OnboardingServiceImpl onboardingService;

  private static final UUID USER_ID = UUID.randomUUID();

  @Nested
  @DisplayName("마이페이지 최신 온보딩 태그 조회")
  class GetMyOnboardingTag {

    @Test
    @DisplayName("userId가 null이면 empty 응답을 반환한다")
    void returnsEmptyWhenUserIdIsNull() {
      MyOnboardingTagResponse response = onboardingService.getMyOnboardingTag(null);

      assertThat(response.hasOnboarding()).isFalse();
      assertThat(response.onboardingId()).isNull();
    }

    @Test
    @DisplayName("활성 온보딩이 없으면 empty 응답을 반환한다")
    void returnsEmptyWhenNoActiveOnboarding() {
      given(onboardingRepository.findActiveByUserId(USER_ID))
          .willReturn(Optional.empty());

      MyOnboardingTagResponse response = onboardingService.getMyOnboardingTag(USER_ID);

      assertThat(response.hasOnboarding()).isFalse();
    }

    @Test
    @DisplayName("활성 온보딩이 있으면 온보딩 태그 정보를 반환한다")
    void returnsOnboardingTagWhenExists() {
      Onboarding onboarding = OnboardingFixture.onboarding(USER_ID);
      given(onboardingRepository.findActiveByUserId(USER_ID))
          .willReturn(Optional.of(onboarding));

      MyOnboardingTagResponse response = onboardingService.getMyOnboardingTag(USER_ID);

      assertThat(response.hasOnboarding()).isTrue();
      assertThat(response.onboardingId()).isEqualTo(onboarding.getId());
      assertThat(response.industry()).isEqualTo(onboarding.getIndustry());
      assertThat(response.serviceType()).isEqualTo(onboarding.getServiceType());
    }
  }

  @Nested
  @DisplayName("마이페이지 최신 온보딩 태그 수정")
  class UpdateMyOnboardingTag {

    @Test
    @DisplayName("userId가 null이면 예외가 발생한다")
    void throwsExceptionWhenUserIdIsNull() {
      UpdateOnboardingTagCommand command = OnboardingFixture.updateTagCommand();

      assertThatThrownBy(() -> onboardingService.updateMyOnboardingTag(null, command))
          .isInstanceOf(OnboardingNotFoundException.class);
    }

    @Test
    @DisplayName("기존 온보딩이 없으면 OnboardingNotFoundException 예외가 발생한다")
    void throwsExceptionWhenOnboardingNotFound() {
      given(onboardingRepository.findActiveByUserIdForUpdate(USER_ID))
          .willReturn(Optional.empty());
      UpdateOnboardingTagCommand command = OnboardingFixture.updateTagCommand();

      assertThatThrownBy(() -> onboardingService.updateMyOnboardingTag(USER_ID, command))
          .isInstanceOf(OnboardingNotFoundException.class);
    }

    @Test
    @DisplayName("순서 상관없이 같은 태그이면 기존 온보딩을 그대로 반환한다")
    void returnsExistingOnboardingWhenTagsAreUnchanged() {
      Onboarding existing = OnboardingFixture.onboarding(USER_ID);
      given(onboardingRepository.findActiveByUserIdForUpdate(USER_ID))
          .willReturn(Optional.of(existing));
      UpdateOnboardingTagCommand unchangedCommand = new UpdateOnboardingTagCommand(
          Category.SHOPPING_COMMERCE,
          ServiceType.MOBILE_APP,
          List.of(AgeBand.AGE_30S, AgeBand.AGE_20S),
          CampaignObjective.IN_APP_ACTION,
          BudgetRange.of(3_000_000L, 10_000_000L),
          CampaignPeriod.M2_3
      );

      MyOnboardingTagResponse response = onboardingService.updateMyOnboardingTag(
          USER_ID, unchangedCommand);

      assertThat(existing.isActive()).isTrue();
      assertThat(response.onboardingId()).isEqualTo(existing.getId());
      assertThat(response.industry()).isEqualTo(Category.SHOPPING_COMMERCE);
      then(onboardingRepository).should(never()).saveAndFlush(any(Onboarding.class));
    }

    @Test
    @DisplayName("태그를 수정하면 기존 온보딩을 비활성화하고 새 온보딩을 생성한다")
    void createsNewOnboarding() {
      Onboarding existing = OnboardingFixture.onboarding(USER_ID);
      given(onboardingRepository.findActiveByUserIdForUpdate(USER_ID))
          .willReturn(Optional.of(existing));
      given(onboardingRepository.saveAndFlush(any(Onboarding.class)))
          .willAnswer(invocation -> invocation.getArgument(0));

      MyOnboardingTagResponse response = onboardingService.updateMyOnboardingTag(
          USER_ID, OnboardingFixture.updateTagCommand());

      assertThat(existing.isActive()).isFalse();

      ArgumentCaptor<Onboarding> captor = ArgumentCaptor.forClass(Onboarding.class);
      then(onboardingRepository).should().saveAndFlush(captor.capture());
      Onboarding created = captor.getValue();

      assertThat(created.getUserId()).isEqualTo(USER_ID);
      assertThat(created.getIndustry()).isEqualTo(Category.FOOD_BEVERAGE);
      assertThat(created.getServiceType()).isEqualTo(ServiceType.MOBILE_APP);
      assertThat(created.getBudgetMin()).isEqualTo(2_000_000L);
      assertThat(created.isActive()).isTrue();
      assertThat(response.hasOnboarding()).isTrue();
    }
  }
}
