package chaeso.zip.server.onboarding.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import chaeso.zip.server.performance.domain.entity.AdPerformance;
import chaeso.zip.server.performance.domain.repository.AdPerformanceRepository;
import chaeso.zip.server.support.PostgresDataJpaTest;
import chaeso.zip.server.support.UserFixture;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 온보딩·집행 실적의 배열 컬럼 매핑과 DB 제약을 실제 적재된 데이터로 검증하는 통합 테스트
 */
@PostgresDataJpaTest
class OnboardingRepositoryTest {

  @Autowired
  private OnboardingRepository onboardingRepository;

  @Autowired
  private AdPerformanceRepository adPerformanceRepository;

  @Autowired
  private TestEntityManager entityManager;

  private UUID persistUser() {
    return entityManager.persistAndFlush(
        UserFixture.user(UUID.randomUUID() + "@example.com")).getId();
  }

  private static Onboarding sample(UUID userId) {
    return Onboarding.create(
        userId,
        "채소집",
        Category.SHOPPING_COMMERCE,
        ServiceType.MOBILE_APP,
        List.of(AgeBand.AGE_20S, AgeBand.AGE_30S),
        CampaignObjective.IN_APP_ACTION,
        3_000_000L,
        10_000_000L,
        CampaignPeriod.M2_3,
        AdExperience.EXPERIENCED);
  }

  @Test
  @DisplayName("연령대 배열을 PostgreSQL text[]로 저장하고 다시 읽어온다")
  void savesAgeBands() {
    UUID userId = persistUser();

    Onboarding saved = onboardingRepository.saveAndFlush(sample(userId));

    assertThat(saved.getTargetAgeBands())
        .containsExactly(AgeBand.AGE_20S, AgeBand.AGE_30S);
  }

  @Test
  @DisplayName("집행 실적을 ad_performances에 저장하고 userId로 조회한다")
  void savesAdPerformances() {
    UUID userId = persistUser();

    adPerformanceRepository.saveAndFlush(AdPerformance.fromOnboarding(
        userId, null, "인스타그램", 3_000_000L, 250_000L, 3_000L, 120L,
        LocalDate.of(2025, Month.MARCH, 1), LocalDate.of(2025, Month.MAY, 31)));

    List<AdPerformance> found = adPerformanceRepository.findByUserId(userId);
    assertThat(found).hasSize(1);
    AdPerformance performance = found.getFirst();
    assertThat(performance.getExternalChannelName()).isEqualTo("인스타그램");
    assertThat(performance.getChannelId()).isNull();
    assertThat(performance.getImpressions()).isEqualTo(250_000L);
    assertThat(performance.getCtrActual()).isEqualByComparingTo("0.012000");
  }

  @Test
  @DisplayName("userId로 조회하면 활성 온보딩만 나온다")
  void findsOnlyActiveResponses() {
    UUID userId = persistUser();
    Onboarding old = sample(userId);
    old.deactivate();
    onboardingRepository.saveAndFlush(old);
    onboardingRepository.saveAndFlush(sample(userId));

    List<Onboarding> actives =
        onboardingRepository.findByUserIdAndIsActiveTrue(userId);

    assertThat(actives).hasSize(1);
    assertThat(actives.getFirst().isActive()).isTrue();
  }

  @Test
  @DisplayName("이전 응답을 비활성화하지 않고 두 번 제출하면 부분 유니크 인덱스 위반으로 실패한다")
  void rejectsTwoActiveResponsesForSameUser() {
    UUID userId = persistUser();
    onboardingRepository.saveAndFlush(sample(userId));

    Onboarding duplicateOnboarding = sample(userId);
    assertThatThrownBy(() -> onboardingRepository.saveAndFlush(duplicateOnboarding))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("채널 식별자가 둘 다 없으면 CHECK 제약 위반으로 실패한다")
  void rejectsPerformanceWithoutAnyChannelIdentifier() {
    UUID userId = persistUser();

    AdPerformance invalidPerformance = AdPerformance.fromOnboarding(
        userId, null, null, 1L, null, null, null, null, null);
    assertThatThrownBy(() -> adPerformanceRepository.saveAndFlush(invalidPerformance))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
