package chaeso.zip.server.recommendation.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.common.ratelimit.RateLimiter;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.recommendation.application.RecommendationService;
import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
import chaeso.zip.server.recommendation.application.dto.SavedRecommendationResponse;
import chaeso.zip.server.recommendation.presentation.dto.SaveRecommendationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

  private static final UUID ONBOARDING_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private RecommendationService recommendationService;

  @MockitoBean
  private RateLimiter rateLimiter;

  @BeforeEach
  void authenticate() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new UserPrincipal(USER_ID), null, List.of()));
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

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
        .andExpect(content().string(not(containsString("shortfallWon"))));
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

  @Test
  @DisplayName("저장 요청이 성공하면 201 과 저장된 추천을 반환한다")
  void saveRecommendation_created() throws Exception {
    UUID channelId = UUID.randomUUID();
    RecommendationItemResponse item = new RecommendationItemResponse(
        channelId, "11번가 광고", 78, "쇼핑·커머스 업종, 설정한 광고 목적에 적합하고 예산 내 집행이 가능해요",
        "20~40대 여성", new BigDecimal("120"), PricingModel.CPM, 3_000L,
        new CountRangeResponse(850_000, 1_150_000), new CountRangeResponse(21_250, 28_750),
        true, null);
    given(recommendationService.save(USER_ID, ONBOARDING_ID))
        .willReturn(new SavedRecommendationResponse(ONBOARDING_ID, 1, List.of(item)));

    mockMvc.perform(post("/api/v1/recommendations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new SaveRecommendationRequest(ONBOARDING_ID))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.onboardingId").value(ONBOARDING_ID.toString()))
        .andExpect(jsonPath("$.data.channelCount").value(1))
        .andExpect(jsonPath("$.data.items[0].channelId").value(channelId.toString()));
  }

  @Test
  @DisplayName("맞는 채널이 없으면 201 과 빈 배열을 반환한다")
  void saveRecommendation_savesNothing() throws Exception {
    given(recommendationService.save(USER_ID, ONBOARDING_ID))
        .willReturn(new SavedRecommendationResponse(ONBOARDING_ID, 0, List.of()));

    mockMvc.perform(post("/api/v1/recommendations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new SaveRecommendationRequest(ONBOARDING_ID))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.channelCount").value(0))
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  @Test
  @DisplayName("onboardingId 없이 저장하면 400 C-001 과 필드 에러를 반환한다")
  void saveRecommendation_missingOnboardingId() throws Exception {
    mockMvc.perform(post("/api/v1/recommendations")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("onboardingId"));
  }

  @Test
  @DisplayName("없는 온보딩이나 남의 온보딩으로 저장하면 404 ONB-007 을 반환한다")
  void saveRecommendation_onboardingNotFound() throws Exception {
    BDDMockito.willThrow(new OnboardingBusinessException(
            OnboardingErrorCode.ONBOARDING_NOT_FOUND, "온보딩 정보가 없습니다. id=" + ONBOARDING_ID))
        .given(recommendationService).save(USER_ID, ONBOARDING_ID);

    mockMvc.perform(post("/api/v1/recommendations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new SaveRecommendationRequest(ONBOARDING_ID))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("ONB-007"));
  }

  @Test
  @DisplayName("저장이 동시에 겹치면 409 ONB-006 을 반환한다")
  void saveRecommendation_concurrent() throws Exception {
    BDDMockito.willThrow(new OnboardingBusinessException(
            OnboardingErrorCode.CONCURRENT_SUBMISSION))
        .given(recommendationService).save(USER_ID, ONBOARDING_ID);

    mockMvc.perform(post("/api/v1/recommendations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new SaveRecommendationRequest(ONBOARDING_ID))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ONB-006"));
  }
}
