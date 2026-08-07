package chaeso.zip.server.recommendation.application;

import static chaeso.zip.server.support.ChannelCatalogFixture.channel;
import static chaeso.zip.server.support.ChannelCatalogFixture.pricing;
import static chaeso.zip.server.support.ChannelCatalogFixture.product;
import static chaeso.zip.server.support.ChannelCatalogFixture.withObjectives;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.Gender;
import chaeso.zip.server.channel.domain.vo.PriceType;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.application.DefaultCtrProvider;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
import chaeso.zip.server.recommendation.application.dto.SavedRecommendationResponse;
import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendation;
import chaeso.zip.server.recommendation.domain.repository.ChannelRecommendationRepository;
import chaeso.zip.server.support.OnboardingFixture;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceImplTest {

  private static final UUID ONBOARDING_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  private static final Category INDUSTRY = Category.MEDICAL_HEALTHCARE;
  private static final CampaignObjective OBJECTIVE = CampaignObjective.AWARENESS;
  private static final AgeBand TARGET_AGE_BAND = AgeBand.AGE_20S;
  private static final AgeBand OTHER_AGE_BAND = AgeBand.AGE_50S_PLUS;

  private static final long BUDGET_MAX = 3_000_000L;

  private static final BigDecimal AVERAGE_CTR = new BigDecimal("2.5");

  @Mock
  private OnboardingRepository onboardingRepository;
  @Mock
  private ChannelRepository channelRepository;
  @Mock
  private ChannelProductRepository channelProductRepository;
  @Mock
  private ChannelPricingRepository channelPricingRepository;
  @Mock
  private ChannelRecommendationRepository channelRecommendationRepository;
  @Mock
  private DefaultCtrProvider defaultCtrProvider;

  @InjectMocks
  private RecommendationServiceImpl recommendationService;

  @Captor
  private ArgumentCaptor<Collection<UUID>> productIdsCaptor;

  @Captor
  private ArgumentCaptor<List<ChannelRecommendation>> savedCaptor;

  @Nested
  @DisplayName("적합도 계산과 순서")
  class Ranking {

    @Test
    @DisplayName("적합도 내림차순으로 반환하고 어느 축도 맞지 않는 채널은 제외한다")
    void ordersByMatchRateAndDropsUnmatched() {
      Channel fullMatch = matchingChannel("세 축 채널", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      Channel twoAxes = matchingChannel("두 축 채널", List.of(INDUSTRY), List.of(OTHER_AGE_BAND));
      Channel oneAxis = matchingChannel("한 축 채널", List.of(Category.GAME),
          List.of(TARGET_AGE_BAND));
      Channel unmatched = matchingChannel("미매칭 채널", List.of(Category.GAME),
          List.of(OTHER_AGE_BAND));

      givenCatalog(
          entry(fullMatch, OBJECTIVE, PricingModel.CPM, "3000"),
          entry(twoAxes, OBJECTIVE, PricingModel.CPM, "3000"),
          entry(oneAxis, CampaignObjective.CONVERSION, PricingModel.CPM, "3000"),
          entry(unmatched, CampaignObjective.CONVERSION, PricingModel.CPM, "3000"));

      List<RecommendationItemResponse> items = recommend(onboarding());

      assertThat(items).extracting(RecommendationItemResponse::channelName)
          .containsExactly("세 축 채널", "두 축 채널", "한 축 채널");
      assertThat(items).extracting(RecommendationItemResponse::matchRate)
          .containsExactly(100, 78, 22);
    }

    @Test
    @DisplayName("적합도가 같으면 집행 가능한 채널을 먼저, 그다음 매체명 순으로 정렬한다")
    void breaksTiesByExecutabilityThenName() {
      // 세 채널 모두 업종만 맞아 적합도가 같다
      Channel expensive = matchingChannel("가매체", List.of(INDUSTRY), List.of(OTHER_AGE_BAND));
      Channel cheapLater = matchingChannel("나매체", List.of(INDUSTRY), List.of(OTHER_AGE_BAND));
      Channel cheapEarlier = matchingChannel("다매체", List.of(INDUSTRY), List.of(OTHER_AGE_BAND));

      givenCatalog(
          entry(expensive, CampaignObjective.CONVERSION, PricingModel.CPM, "9000000"),
          entry(cheapLater, CampaignObjective.CONVERSION, PricingModel.CPM, "3000"),
          entry(cheapEarlier, CampaignObjective.CONVERSION, PricingModel.CPM, "3000"));

      List<RecommendationItemResponse> items = recommend(onboarding());

      assertThat(items).extracting(RecommendationItemResponse::channelName)
          .containsExactly("나매체", "다매체", "가매체");   // 집행 불가인 "가매체" 가 뒤로
      assertThat(items).extracting(RecommendationItemResponse::matchRate)
          .containsOnly(44);
    }

    @Test
    @DisplayName("적합도가 있는 채널이 많아도 상위 8개까지만 반환한다")
    void limitsToTopEight() {
      List<CatalogEntry> entries = new ArrayList<>();
      for (int order = 1; order <= 9; order++) {
        Channel channel = matchingChannel("채널%02d".formatted(order), List.of(INDUSTRY),
            List.of(OTHER_AGE_BAND));
        entries.add(entry(channel, CampaignObjective.CONVERSION, PricingModel.CPM, "3000"));
      }
      givenCatalog(entries.toArray(CatalogEntry[]::new));

      List<RecommendationItemResponse> items = recommend(onboarding());

      assertThat(items).hasSize(RecommendationServiceImpl.MAX_ITEMS);
      assertThat(items).extracting(RecommendationItemResponse::channelName)
          .doesNotContain("채널09");   // 개수를 맞추려 순서를 흐트러뜨리지 않는다
    }

    @Test
    @DisplayName("맞는 채널이 하나도 없으면 0점 채널로 채우지 않고 빈 목록을 반환한다")
    void returnsEmptyWhenNothingMatches() {
      Channel unmatched = matchingChannel("미매칭 채널", List.of(Category.GAME),
          List.of(OTHER_AGE_BAND));
      given(channelRepository.findByActiveTrue()).willReturn(List.of(unmatched));
      given(channelProductRepository.findByChannelIdIn(anyCollection())).willReturn(
          List.of(withObjectives(product(UUID.randomUUID(), unmatched.getId()),
              CampaignObjective.CONVERSION)));

      assertThat(recommend(onboarding())).isEmpty();

      // 후보가 없으면 단가를 조회하거나 CTR 을 집계할 이유가 없다
      verifyNoInteractions(channelPricingRepository, defaultCtrProvider);
    }

    @Test
    @DisplayName("활성 채널이 없으면 상품 조회 없이 빈 목록을 반환한다")
    void returnsEmptyWithoutActiveChannels() {
      given(channelRepository.findByActiveTrue()).willReturn(List.of());

      assertThat(recommend(onboarding())).isEmpty();

      verifyNoInteractions(channelProductRepository, channelPricingRepository, defaultCtrProvider);
    }

    @Test
    @DisplayName("매칭되지 않은 채널의 단가는 조회하지 않는다")
    void loadsPricingOnlyForMatchedChannels() {
      Channel matched = matchingChannel("매칭 채널", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      Channel unmatched = matchingChannel("미매칭 채널", List.of(Category.GAME),
          List.of(OTHER_AGE_BAND));
      CatalogEntry matchedEntry = entry(matched, OBJECTIVE, PricingModel.CPM, "3000");
      CatalogEntry unmatchedEntry =
          entry(unmatched, CampaignObjective.CONVERSION, PricingModel.CPM, "3000");
      givenCatalog(matchedEntry, unmatchedEntry);

      recommend(onboarding());

      verify(channelPricingRepository).findByChannelProductIdIn(productIdsCaptor.capture());
      assertThat(productIdsCaptor.getValue())
          .containsExactly(matchedEntry.product().getId());
    }
  }

  @Nested
  @DisplayName("집행 가능 여부와 추정")
  class Estimation {

    @Test
    @DisplayName("온보딩 예산 상한을 기준으로 노출·클릭을 추정하고 클릭당 비용을 환산한다")
    void estimatesWithOnboardingBudget() {
      Channel channel = matchingChannel("11번가 광고", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      givenCatalog(entry(channel, OBJECTIVE, PricingModel.CPM, "3000"));

      RecommendationItemResponse item = recommend(onboarding()).getFirst();

      assertThat(item.channelId()).isEqualTo(channel.getId());
      assertThat(item.matchRate()).isEqualTo(100);
      assertThat(item.isExecutable()).isTrue();
      assertThat(item.shortfallWon()).isNull();
      assertThat(item.minBudgetWon()).isEqualTo(3_000);
      assertThat(item.pricingModel()).isEqualTo(PricingModel.CPM);
      assertThat(item.primaryTarget()).isEqualTo("30대 여성");
      // 예산 상한 3,000,000 / CPM 3,000 * 1000 = 1,000,000 노출, ±15%
      assertThat(item.estImpressions()).isEqualTo(new CountRangeResponse(850_000, 1_150_000));
      // 상품에 CTR 이 없어 카탈로그 평균 2.5% 적용
      assertThat(item.estClicks()).isEqualTo(new CountRangeResponse(21_250, 28_750));
      // 3,000,000 / 25,000 클릭(중앙값) = 120 원
      assertThat(item.cpcWon()).isEqualByComparingTo("120");
      assertThat(item.recommendationReason())
          .isEqualTo("의료·헬스케어 업종, 설정한 광고 목적, 타깃 연령대에 적합하고 예산 내 집행이 가능해요");
    }

    @Test
    @DisplayName("예산이 최소 단가에 못 미쳐도 추천에 남기고, 최소 집행 기준으로 노출·클릭과 부족액을 준다")
    void keepsNotExecutableChannelWithMinimumExecutionEstimate() {
      Channel channel = matchingChannel("당근마켓 광고", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      // 구좌 30일 · 구좌당 100만 노출 · CTR 2% · 단가 500만원 (예산 상한 300만원으로는 부족)
      ChannelProduct product = withObjectives(
          product(UUID.randomUUID(), channel.getId(), new BigDecimal("2"), 1_000_000L, null),
          OBJECTIVE);
      givenCatalog(new CatalogEntry(channel, product,
          pricing(product.getId(), PricingModel.SLOT, PriceType.LIST, "5000000", null, "30")));

      RecommendationItemResponse item = recommend(onboarding()).getFirst();

      assertThat(item.isExecutable()).isFalse();
      assertThat(item.minBudgetWon()).isEqualTo(5_000_000);
      assertThat(item.shortfallWon()).isEqualTo(2_000_000);   // 500만 - 예산 상한 300만
      // 최소 단가로 1구좌 집행했을 때 기준: 100만 노출 ±15%
      assertThat(item.estImpressions()).isEqualTo(new CountRangeResponse(850_000, 1_150_000));
      assertThat(item.estClicks()).isEqualTo(new CountRangeResponse(17_000, 23_000));
      // 환산 기준도 최소 집행 예산: 5,000,000 / 20,000 클릭 = 250 원
      assertThat(item.cpcWon()).isEqualByComparingTo("250");
      assertThat(item.recommendationReason()).isEqualTo(
          "의료·헬스케어 업종, 설정한 광고 목적, 타깃 연령대에 적합하지만 집행에는 2,000,000원이 더 필요해요");
    }

    @Test
    @DisplayName("시뮬레이터와 같이 노출을 낼 수 있는 상품을 대표로 삼는다")
    void picksSameRepresentativeProductAsSimulator() {
      Channel channel = matchingChannel("11번가 광고", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      // 클릭당 10원 상품이 뽑히면 노출을 전혀 못 내고 "10원이면 집행 가능"으로 읽힌다
      ChannelProduct cheapCpc = withObjectives(product(UUID.randomUUID(), channel.getId()),
          OBJECTIVE);
      ChannelProduct estimable = product(UUID.randomUUID(), channel.getId(),
          new BigDecimal("2"), 15_000L, null);
      given(onboardingRepository.findById(ONBOARDING_ID)).willReturn(Optional.of(onboarding()));
      given(channelRepository.findByActiveTrue()).willReturn(List.of(channel));
      given(channelProductRepository.findByChannelIdIn(anyCollection()))
          .willReturn(List.of(cheapCpc, estimable));
      given(channelPricingRepository.findByChannelProductIdIn(anyCollection())).willReturn(List.of(
          pricing(cheapCpc.getId(), PricingModel.CPC, "10"),
          pricing(estimable.getId(), PricingModel.SLOT, PriceType.LIST, "150000", null, "1")));
      given(defaultCtrProvider.averageCtrPercent()).willReturn(AVERAGE_CTR);

      RecommendationItemResponse item =
          recommendationService.recommend(ONBOARDING_ID).getFirst();

      assertThat(item.pricingModel()).isEqualTo(PricingModel.SLOT);
      assertThat(item.minBudgetWon()).isEqualTo(150_000);
      assertThat(item.estImpressions()).isNotNull();
    }

    @Test
    @DisplayName("클릭당 과금 매체는 단가를 환산 없이 클릭당 비용으로 준다")
    void usesCpcPriceAsIs() {
      Channel channel = matchingChannel("클릭 과금 채널", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      givenCatalog(entry(channel, OBJECTIVE, PricingModel.CPC, "500"));

      RecommendationItemResponse item = recommend(onboarding()).getFirst();

      assertThat(item.cpcWon()).isEqualByComparingTo("500");
      assertThat(item.isExecutable()).isTrue();
      assertThat(item.estImpressions()).isNull();   // 노출 근거가 없는 상품
    }

    @Test
    @DisplayName("등록된 단가가 없는 채널은 적합도만 주고 금액·추정 칸을 비운다")
    void keepsMatchedChannelWithoutPricing() {
      Channel channel = matchingChannel("견적 문의 채널", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      ChannelProduct product =
          withObjectives(product(UUID.randomUUID(), channel.getId()), OBJECTIVE);
      givenCatalog(new CatalogEntry(channel, product,
          pricing(product.getId(), PricingModel.CPM, PriceType.LIST, null, null, null)));

      RecommendationItemResponse item = recommend(onboarding()).getFirst();

      assertThat(item.matchRate()).isEqualTo(100);
      assertThat(item.isExecutable()).isFalse();
      assertThat(item.minBudgetWon()).isNull();
      assertThat(item.shortfallWon()).isNull();
      assertThat(item.pricingModel()).isNull();
      assertThat(item.cpcWon()).isNull();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
      assertThat(item.recommendationReason()).endsWith("등록된 단가가 없어 집행 금액은 문의가 필요해요");
    }
  }

  @Test
  @DisplayName("온보딩이 없으면 추천하지 않고 거부한다")
  void rejectsUnknownOnboarding() {
    given(onboardingRepository.findById(ONBOARDING_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> recommendationService.recommend(ONBOARDING_ID))
        .isInstanceOf(OnboardingBusinessException.class)
        .hasMessageContaining(ONBOARDING_ID.toString());

    verifyNoInteractions(channelRepository, channelProductRepository, channelPricingRepository);
  }

  @Nested
  @DisplayName("추천 결과 저장 (POST /recommendations)")
  class Save {

    @Test
    @DisplayName("추천된 채널을 각각 한 행으로 저장하고 순위를 1부터 매긴다")
    void savesOneRowPerChannelWithRank() {
      Channel best = matchingChannel("가매체", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      Channel second = matchingChannel("나매체", List.of(INDUSTRY), List.of(OTHER_AGE_BAND));
      givenCatalog(
          entry(best, OBJECTIVE, PricingModel.CPM, "3000"),
          entry(second, OBJECTIVE, PricingModel.CPM, "3000"));

      SavedRecommendationResponse response = save(ownedOnboarding());

      assertThat(response.onboardingId()).isEqualTo(ONBOARDING_ID);
      assertThat(response.channelCount()).isEqualTo(2);

      verify(channelRecommendationRepository).saveAll(savedCaptor.capture());
      assertThat(savedCaptor.getValue())
          .extracting(ChannelRecommendation::getChannelName, ChannelRecommendation::getRank)
          .containsExactly(tuple("가매체", 1), tuple("나매체", 2));
      assertThat(savedCaptor.getValue())
          .allSatisfy(row -> {
            assertThat(row.getUserId()).isEqualTo(USER_ID);
            assertThat(row.getOnboardingId()).isEqualTo(ONBOARDING_ID);
          });
    }

    @Test
    @DisplayName("추천 시점의 계산 결과를 그대로 박제한다")
    void storesCalculatedValuesAsSnapshot() {
      Channel channel = matchingChannel("가매체", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      givenCatalog(entry(channel, OBJECTIVE, PricingModel.CPM, "3000"));

      RecommendationItemResponse calculated = save(ownedOnboarding()).items().getFirst();

      verify(channelRecommendationRepository).saveAll(savedCaptor.capture());
      ChannelRecommendation saved = savedCaptor.getValue().getFirst();

      // 응답으로 나간 값과 저장된 값이 같아야 재계산 없이 그때 화면을 복원할 수 있다
      assertThat(saved.getChannelId()).isEqualTo(calculated.channelId());
      assertThat(saved.getChannelName()).isEqualTo(calculated.channelName());
      assertThat(saved.getScore()).isEqualTo(calculated.matchRate());
      assertThat(saved.getReason()).isEqualTo(calculated.recommendationReason());
      assertThat(saved.getAudienceSummarySnap()).isEqualTo(calculated.primaryTarget());
      assertThat(saved.getEstPricingModel()).isEqualTo(calculated.pricingModel());
      assertThat(saved.getMinBudgetWonSnap()).isEqualTo(calculated.minBudgetWon());
      assertThat(saved.getCpcWon()).isEqualByComparingTo(calculated.cpcWon());
      assertThat(saved.getEstImpressionsMin()).isEqualTo(calculated.estImpressions().min());
      assertThat(saved.getEstImpressionsMax()).isEqualTo(calculated.estImpressions().max());
      assertThat(saved.getEstClicksMin()).isEqualTo(calculated.estClicks().min());
      assertThat(saved.getEstClicksMax()).isEqualTo(calculated.estClicks().max());
      assertThat(saved.isExecutable()).isEqualTo(calculated.isExecutable());
      assertThat(saved.getShortfallWon()).isEqualTo(calculated.shortfallWon());

      // 응답에는 없고 저장에만 있는 값
      assertThat(saved.getEstUnitPrice()).isEqualByComparingTo("3000");
      assertThat(saved.getReasonTags()).containsExactly("CATEGORY", "OBJECTIVE", "AGE_BAND");
      assertThat(saved.getPricingModelsAll()).containsExactly("CPM");
    }

    @Test
    @DisplayName("대표 단가가 없는 매체도 저장하고, 추정값 자리는 비워 둔다")
    void savesQuoteRequiredChannelWithoutEstimates() {
      Channel channel = matchingChannel("단가없는매체", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      ChannelProduct product =
          withObjectives(product(UUID.randomUUID(), channel.getId()), OBJECTIVE);
      given(channelRepository.findByActiveTrue()).willReturn(List.of(channel));
      given(channelProductRepository.findByChannelIdIn(anyCollection()))
          .willReturn(List.of(product));
      given(channelPricingRepository.findByChannelProductIdIn(anyCollection()))
          .willReturn(List.of());
      given(defaultCtrProvider.averageCtrPercent()).willReturn(AVERAGE_CTR);

      save(ownedOnboarding());

      verify(channelRecommendationRepository).saveAll(savedCaptor.capture());
      ChannelRecommendation saved = savedCaptor.getValue().getFirst();

      assertThat(saved.getEstPricingModel()).isNull();
      assertThat(saved.getEstUnitPrice()).isNull();
      assertThat(saved.getEstImpressionsMin()).isNull();
      assertThat(saved.getEstClicksMax()).isNull();
      assertThat(saved.getMinBudgetWonSnap()).isNull();
      assertThat(saved.isExecutable()).isFalse();
      assertThat(saved.getPricingModelsAll()).isEmpty();
      // 근거는 단가와 무관하게 매칭 축으로 만들어지므로 남는다
      assertThat(saved.getReason()).isNotBlank();
      assertThat(saved.getReasonTags()).isNotEmpty();
    }

    @Test
    @DisplayName("같은 온보딩으로 다시 저장하면 이전 추천을 지우고 다시 넣는다")
    void overwritesPreviousRecommendation() {
      Channel channel = matchingChannel("가매체", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      givenCatalog(entry(channel, OBJECTIVE, PricingModel.CPM, "3000"));

      save(ownedOnboarding());

      InOrder inOrder = inOrder(channelRecommendationRepository);
      inOrder.verify(channelRecommendationRepository).deleteByOnboardingId(ONBOARDING_ID);
      inOrder.verify(channelRecommendationRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("지우고 넣기 전에 온보딩 행을 잠가 같은 온보딩의 저장을 직렬화한다")
    void locksOnboardingBeforeRewriting() {
      Channel channel = matchingChannel("가매체", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      givenCatalog(entry(channel, OBJECTIVE, PricingModel.CPM, "3000"));

      save(ownedOnboarding());

      InOrder inOrder = inOrder(channelRepository, onboardingRepository,
          channelRecommendationRepository);
      inOrder.verify(channelRepository).findByActiveTrue();
      inOrder.verify(onboardingRepository).findByIdForUpdate(ONBOARDING_ID);
      inOrder.verify(channelRecommendationRepository).deleteByOnboardingId(ONBOARDING_ID);
      inOrder.verify(channelRecommendationRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("맞는 채널이 없으면 저장할 것도 없어 빈 결과를 반환한다")
    void savesNothingWhenNothingMatches() {
      Channel unmatched = matchingChannel("미매칭 채널", List.of(Category.GAME),
          List.of(OTHER_AGE_BAND));
      given(channelRepository.findByActiveTrue()).willReturn(List.of(unmatched));
      given(channelProductRepository.findByChannelIdIn(anyCollection())).willReturn(List.of());

      SavedRecommendationResponse response = save(ownedOnboarding());

      assertThat(response.channelCount()).isZero();
      assertThat(response.items()).isEmpty();
      // 덮어쓰기는 그대로 수행한다. 이번 추천이 비었다면 이전 저장분도 남아 있으면 안 된다
      verify(channelRecommendationRepository).deleteByOnboardingId(ONBOARDING_ID);
      verify(channelRecommendationRepository).saveAll(List.of());
    }

    @Test
    @DisplayName("다른 사용자가 제출한 온보딩은 없는 것과 같은 404 로 숨기고 저장하지 않는다")
    void rejectsOtherUsersOnboarding() {
      Onboarding othersOnboarding = onboarding(UUID.randomUUID());

      assertThatThrownBy(() -> save(othersOnboarding))
          .isInstanceOf(OnboardingBusinessException.class)
          .hasMessageContaining(ONBOARDING_ID.toString());

      verifyNoInteractions(channelRecommendationRepository, channelRepository);
    }

    @Test
    @DisplayName("비로그인으로 제출해 주인이 없는 온보딩도 저장할 수 없다")
    void rejectsAnonymousOnboarding() {
      Onboarding anonymous = onboarding(null);

      assertThatThrownBy(() -> save(anonymous))
          .isInstanceOf(OnboardingBusinessException.class);

      verifyNoInteractions(channelRecommendationRepository, channelRepository);
    }

    @Test
    @DisplayName("존재하지 않는 온보딩은 404 로 거부한다")
    void rejectsUnknownOnboarding() {
      given(onboardingRepository.findById(ONBOARDING_ID)).willReturn(Optional.empty());

      assertThatThrownBy(() -> recommendationService.save(USER_ID, ONBOARDING_ID))
          .isInstanceOf(OnboardingBusinessException.class);

      verifyNoInteractions(channelRecommendationRepository, channelRepository);
    }

    @Test
    @DisplayName("같은 온보딩으로 저장이 동시에 겹치면 유니크 제약 위반을 409 로 바꿔 준다")
    void translatesConcurrentSaveToConflict() {
      Channel channel = matchingChannel("가매체", List.of(INDUSTRY), List.of(TARGET_AGE_BAND));
      givenCatalog(entry(channel, OBJECTIVE, PricingModel.CPM, "3000"));
      given(channelRecommendationRepository.saveAll(anyList()))
          .willThrow(new DataIntegrityViolationException("duplicate key"));

      assertThatThrownBy(() -> save(ownedOnboarding()))
          .isInstanceOf(OnboardingBusinessException.class)
          .extracting(e -> ((OnboardingBusinessException) e).getErrorCode())
          .isEqualTo(OnboardingErrorCode.CONCURRENT_SUBMISSION);
    }

    private SavedRecommendationResponse save(Onboarding onboarding) {
      given(onboardingRepository.findById(ONBOARDING_ID)).willReturn(Optional.of(onboarding));
      return recommendationService.save(USER_ID, ONBOARDING_ID);
    }

    /** 저장하는 사용자가 직접 제출한 온보딩. */
    private Onboarding ownedOnboarding() {
      return onboarding(USER_ID);
    }

    private Onboarding onboarding(UUID userId) {
      return OnboardingFixture.onboarding(userId, INDUSTRY, OBJECTIVE, List.of(TARGET_AGE_BAND),
          1_000_000L, BUDGET_MAX, CampaignPeriod.M1);
    }
  }

  private List<RecommendationItemResponse> recommend(Onboarding onboarding) {
    given(onboardingRepository.findById(ONBOARDING_ID)).willReturn(Optional.of(onboarding));
    return recommendationService.recommend(ONBOARDING_ID);
  }

  /** 업종·목적·연령이 모두 맞는 온보딩. 예산 상한 300만원, 기간 1개월. */
  private static Onboarding onboarding() {
    return OnboardingFixture.onboarding(INDUSTRY, OBJECTIVE, List.of(TARGET_AGE_BAND),
        1_000_000L, BUDGET_MAX, CampaignPeriod.M1);
  }

  private static Channel matchingChannel(String name, List<Category> suitableCategories,
      List<AgeBand> ageBandCodes) {
    return channel(UUID.randomUUID(), name, suitableCategories, ageBandCodes, "30대",
        Gender.FEMALE);
  }

  private static CatalogEntry entry(Channel channel, CampaignObjective objective,
      PricingModel pricingModel, String price) {
    ChannelProduct product =
        withObjectives(product(UUID.randomUUID(), channel.getId()), objective);
    return new CatalogEntry(channel, product,
        pricing(product.getId(), pricingModel, price));
  }

  private void givenCatalog(CatalogEntry... entries) {
    given(channelRepository.findByActiveTrue())
        .willReturn(Arrays.stream(entries).map(CatalogEntry::channel).toList());
    given(channelProductRepository.findByChannelIdIn(anyCollection()))
        .willReturn(Arrays.stream(entries).map(CatalogEntry::product).toList());
    given(channelPricingRepository.findByChannelProductIdIn(anyCollection()))
        .willReturn(Arrays.stream(entries).map(CatalogEntry::pricing).toList());
    given(defaultCtrProvider.averageCtrPercent()).willReturn(AVERAGE_CTR);
  }

  /** 채널 하나와 그 채널의 단일 상품·단가. */
  private record CatalogEntry(Channel channel, ChannelProduct product, ChannelPricing pricing) {
  }
}
