package chaeso.zip.server.recommendation.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.common.ratelimit.RateLimiter;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.recommendation.application.RecommendationService;
import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

  private static final UUID ONBOARDING_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private RecommendationService recommendationService;

  @MockitoBean
  private RateLimiter rateLimiter;

  @Test
  @DisplayName("추천 조회가 성공하면 200 과 적합도·추정값을 반환하고 enum 은 코드값으로 직렬화된다")
  void getRecommendations_success() throws Exception {
    UUID channelId = UUID.randomUUID();
    RecommendationItemResponse item = new RecommendationItemResponse(
        channelId, "11번가 광고", 78, "쇼핑·커머스 업종, 설정한 광고 목적에 적합하고 예산 내 집행이 가능해요",
        "20~40대 여성", new BigDecimal("120"), PricingModel.CPM, 3_000L,
        new CountRangeResponse(850_000, 1_150_000), new CountRangeResponse(21_250, 28_750),
        true, null);

    given(recommendationService.recommend(ONBOARDING_ID)).willReturn(List.of(item));

    mockMvc.perform(get("/api/v1/recommendations").param("onboardingId", ONBOARDING_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].channelId").value(channelId.toString()))
        .andExpect(jsonPath("$.data[0].channelName").value("11번가 광고"))
        .andExpect(jsonPath("$.data[0].matchRate").value(78))
        .andExpect(jsonPath("$.data[0].primaryTarget").value("20~40대 여성"))
        .andExpect(jsonPath("$.data[0].pricingModel").value("CPM"))
        .andExpect(jsonPath("$.data[0].minBudgetWon").value(3000))
        .andExpect(jsonPath("$.data[0].estImpressions.min").value(850000))
        .andExpect(jsonPath("$.data[0].estClicks.max").value(28750))
        .andExpect(jsonPath("$.data[0].isExecutable").value(true))
        .andExpect(jsonPath("$.data[0].shortfallWon").doesNotExist());
  }

  @Test
  @DisplayName("맞는 채널이 없으면 200 과 빈 배열을 반환한다")
  void getRecommendations_empty() throws Exception {
    given(recommendationService.recommend(ONBOARDING_ID)).willReturn(List.of());

    mockMvc.perform(get("/api/v1/recommendations").param("onboardingId", ONBOARDING_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  @DisplayName("존재하지 않는 온보딩으로 조회하면 404 와 공통 에러 포맷을 반환한다")
  void getRecommendations_onboardingNotFound() throws Exception {
    BDDMockito.willThrow(new OnboardingBusinessException(
            OnboardingErrorCode.ONBOARDING_NOT_FOUND, "온보딩 정보가 없습니다. id=" + ONBOARDING_ID))
        .given(recommendationService).recommend(ONBOARDING_ID);

    mockMvc.perform(get("/api/v1/recommendations").param("onboardingId", ONBOARDING_ID.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("ONB-007"));
  }

  @Test
  @DisplayName("onboardingId 없이 호출하면 400 으로 거부한다")
  void getRecommendations_missingOnboardingId() throws Exception {
    mockMvc.perform(get("/api/v1/recommendations"))
        .andExpect(status().isBadRequest());
  }
}
