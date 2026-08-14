package chaeso.zip.server.onboarding.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.common.ratelimit.RateLimiter;
import chaeso.zip.server.onboarding.application.OnboardingService;
import chaeso.zip.server.onboarding.application.dto.MyOnboardingTagResponse;
import chaeso.zip.server.onboarding.application.dto.OnboardingSubmitResponse;
import chaeso.zip.server.onboarding.application.dto.PresignedFileUploadResult;
import chaeso.zip.server.onboarding.application.dto.SubmitOnboardingCommand;
import chaeso.zip.server.onboarding.application.dto.UpdateOnboardingTagCommand;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import chaeso.zip.server.onboarding.presentation.dto.AdHistoryRequest;
import chaeso.zip.server.onboarding.presentation.dto.PerformanceFileMeta;
import chaeso.zip.server.onboarding.presentation.dto.PresignPerformanceFilesRequest;
import chaeso.zip.server.onboarding.presentation.dto.SubmitOnboardingRequest;
import chaeso.zip.server.onboarding.presentation.dto.UpdateOnboardingTagRequest;
import chaeso.zip.server.support.OnboardingFixture;
import chaeso.zip.server.support.security.WithUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(OnboardingController.class)
@WithUserPrincipal
class OnboardingControllerTest {

  private static final UUID USER_ID = UUID.fromString(WithUserPrincipal.DEFAULT_USER_ID);

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private OnboardingService onboardingService;

  @MockitoBean
  private RateLimiter rateLimiter;

  @Nested
  @DisplayName("온보딩을 제출한다")
  class Submit {

    @Test
    @DisplayName("온보딩 제출에 성공하면 201과 생성된 온보딩 id, 생성 시각을 반환한다")
    void submitReturnsCreated() throws Exception {
      UUID onboardingId = UUID.randomUUID();
      LocalDateTime createdAt = LocalDateTime.of(2026, Month.JULY, 23, 10, 0, 0);
      given(onboardingService.submit(eq(USER_ID), any(SubmitOnboardingCommand.class)))
          .willReturn(new OnboardingSubmitResponse(onboardingId, createdAt));

      mockMvc.perform(post("/api/v1/onboarding")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(OnboardingFixture.submitRequest())))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.onboardingId").value(onboardingId.toString()))
          .andExpect(jsonPath("$.data.createdAt").value("2026-07-23T10:00:00"));
    }

    @Test
    @DisplayName("비로그인 사용자도 온보딩 제출에 성공하고 사용자 식별자 없이 처리된다")
    @WithAnonymousUser
    void submitSucceedsAnonymously() throws Exception {
      UUID onboardingId = UUID.randomUUID();
      LocalDateTime createdAt = LocalDateTime.of(2026, Month.JULY, 23, 10, 0, 0);
      given(onboardingService.submit(isNull(), any(SubmitOnboardingCommand.class)))
          .willReturn(new OnboardingSubmitResponse(onboardingId, createdAt));

      mockMvc.perform(post("/api/v1/onboarding")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(OnboardingFixture.submitRequest())))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("연령대가 비어 있으면 400 C-001과 필드 에러를 반환한다")
    void rejectsEmptyAgeBands() throws Exception {
      SubmitOnboardingRequest request = new SubmitOnboardingRequest(
          "채소집", Category.SHOPPING_COMMERCE, ServiceType.WEB, List.of(),
          CampaignObjective.TRAFFIC, 1L, 2L, CampaignPeriod.M1, AdExperience.NONE, List.of(),
          List.of());

      mockMvc.perform(post("/api/v1/onboarding")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("C-001"))
          .andExpect(jsonPath("$.error.fieldErrors[0].field").value("targetAgeBands"));
    }

    @Test
    @DisplayName("예산이 0원 미만이면 400 C-001을 반환한다")
    void rejectsNegativeBudget() throws Exception {
      SubmitOnboardingRequest request = OnboardingFixture.submitRequest(-1L, 2L);

      mockMvc.perform(post("/api/v1/onboarding")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("C-001"))
          .andExpect(jsonPath("$.error.fieldErrors[0].field").value("budgetMin"));
    }

    @Test
    @DisplayName("예산이 1,000만원을 초과하면 400 C-001을 반환한다")
    void rejectsBudgetOverTenMillionWon() throws Exception {
      SubmitOnboardingRequest request = OnboardingFixture.submitRequest(1L, 10_000_001L);

      mockMvc.perform(post("/api/v1/onboarding")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("C-001"))
          .andExpect(jsonPath("$.error.fieldErrors[0].field").value("budgetMax"));
    }

    @Test
    @DisplayName("수동 입력 집행 내역이 3건을 넘으면 400 C-001")
    void rejectsTooManyAdHistoryRows() throws Exception {
      AdHistoryRequest row = new AdHistoryRequest(null, "인스타그램", 1000L, 10_000L, null, null, null);
      SubmitOnboardingRequest request = new SubmitOnboardingRequest(
          "채소집", Category.SHOPPING_COMMERCE, ServiceType.WEB,
          List.of(AgeBand.AGE_20S), CampaignObjective.TRAFFIC, 1L, 2L, CampaignPeriod.M1,
          AdExperience.EXPERIENCED, List.of(row, row, row, row), List.of());

      mockMvc.perform(post("/api/v1/onboarding")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("C-001"))
          .andExpect(jsonPath("$.error.fieldErrors[0].field").value("adHistory"));
    }

    @Test
    @DisplayName("성과파일 key가 5개를 넘으면 400 C-001")
    void rejectsTooManyRawFileKeys() throws Exception {
      List<String> rawFileKeys = List.of("a.xlsx", "b.xlsx", "c.xlsx", "d.xlsx", "e.xlsx", "f.xlsx");
      SubmitOnboardingRequest request = new SubmitOnboardingRequest(
          "채소집", Category.SHOPPING_COMMERCE, ServiceType.WEB,
          List.of(AgeBand.AGE_20S), CampaignObjective.TRAFFIC, 1L, 2L, CampaignPeriod.M1,
          AdExperience.EXPERIENCED, List.of(), rawFileKeys);

      mockMvc.perform(post("/api/v1/onboarding")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("C-001"))
          .andExpect(jsonPath("$.error.fieldErrors[0].field").value("rawFileKeys"));
    }

    @Test
    @DisplayName("서비스가 비즈니스 예외를 던지면 매핑된 상태 코드와 에러 코드로 응답한다")
    void propagatesObjectiveNotAllowed() throws Exception {
      willThrow(new OnboardingBusinessException(OnboardingErrorCode.OBJECTIVE_NOT_ALLOWED))
          .given(onboardingService).submit(eq(USER_ID), any(SubmitOnboardingCommand.class));

      mockMvc.perform(post("/api/v1/onboarding")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(OnboardingFixture.submitRequest())))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("ONB-002"));
    }
  }

  @Nested
  @DisplayName("성과파일 업로드 URL을 발급한다")
  class Presign {

    @Test
    @DisplayName("업로드 URL 발급은 인증 없이도 성공한다")
    @WithAnonymousUser
    void presignSucceedsWithoutAuth() throws Exception {
      given(onboardingService.issuePresignedUrls(any()))
          .willReturn(List.of(new PresignedFileUploadResult(
              "ad-history/abc.xlsx", "https://example.com/upload",
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
              Instant.parse("2026-07-27T00:05:00Z"))));

      PresignPerformanceFilesRequest request = new PresignPerformanceFilesRequest(List.of(
          new PerformanceFileMeta("실적.xlsx", 1024L)));

      mockMvc.perform(post("/api/v1/onboarding/ad-history/presigned-urls")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data[0].key").value("ad-history/abc.xlsx"));
    }

    @Test
    @DisplayName("파일 6개를 요청하면 400 C-001")
    void presignRejectsTooManyFiles() throws Exception {
      PerformanceFileMeta meta = new PerformanceFileMeta("실적.xlsx", 1024L);
      PresignPerformanceFilesRequest request =
          new PresignPerformanceFilesRequest(List.of(meta, meta, meta, meta, meta, meta));

      mockMvc.perform(post("/api/v1/onboarding/ad-history/presigned-urls")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("C-001"));
    }

    @Test
    @DisplayName("xlsx/csv가 아닌 확장자는 400 C-001")
    void presignRejectsDisallowedExtension() throws Exception {
      PresignPerformanceFilesRequest request = new PresignPerformanceFilesRequest(List.of(
          new PerformanceFileMeta("실적.pdf", 1024L)));

      mockMvc.perform(post("/api/v1/onboarding/ad-history/presigned-urls")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("C-001"));
    }
  }

  @Nested
  @DisplayName("최신 집행 온보딩 태그를 조회한다")
  class GetMyOnboardingTag {

    @Test
    @DisplayName("온보딩이 존재하면 200 OK와 태그 정보를 반환한다")
    void returnsOnboardingTagWhenExists() throws Exception {
      UUID onboardingId = UUID.randomUUID();
      Onboarding onboarding = OnboardingFixture.onboarding(USER_ID);
      ReflectionTestUtils.setField(onboarding, "id", onboardingId);
      given(onboardingService.getMyOnboardingTag(USER_ID))
          .willReturn(MyOnboardingTagResponse.from(onboarding));

      mockMvc.perform(get("/api/v1/onboarding/me/tags"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.hasOnboarding").value(true))
          .andExpect(jsonPath("$.data.onboardingId").value(onboardingId.toString()))
          .andExpect(jsonPath("$.data.industry").value("SHOPPING_COMMERCE"));
    }

    @Test
    @DisplayName("온보딩이 존재하지 않으면 200 OK와 hasOnboarding = false를 반환한다")
    void returnsHasOnboardingFalseWhenEmpty() throws Exception {
      given(onboardingService.getMyOnboardingTag(USER_ID))
          .willReturn(MyOnboardingTagResponse.empty());

      mockMvc.perform(get("/api/v1/onboarding/me/tags"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.hasOnboarding").value(false))
          .andExpect(jsonPath("$.data.onboardingId").isEmpty())
          .andExpect(jsonPath("$.data.targetAgeBands").isArray())
          .andExpect(jsonPath("$.data.targetAgeBands").isEmpty());
    }
  }

  @Nested
  @DisplayName("최신 집행 온보딩 태그를 수정한다")
  class UpdateMyOnboardingTag {

    @Test
    @DisplayName("유효한 태그 정보로 수정 시 200 OK와 수정된 태그 정보를 반환한다")
    void updateTagReturnsOk() throws Exception {
      UpdateOnboardingTagRequest request = new UpdateOnboardingTagRequest(
          Category.FOOD_BEVERAGE,
          ServiceType.MOBILE_APP,
          List.of(AgeBand.AGE_20S),
          CampaignObjective.CONVERSION,
          2_000_000L,
          10_000_000L,
          CampaignPeriod.M2_3
      );

      Onboarding onboarding = OnboardingFixture.onboarding(USER_ID);
      given(onboardingService.updateMyOnboardingTag(eq(USER_ID), any(UpdateOnboardingTagCommand.class)))
          .willReturn(MyOnboardingTagResponse.from(onboarding));

      mockMvc.perform(put("/api/v1/onboarding/me/tags")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.hasOnboarding").value(true));
    }

    @Test
    @DisplayName("필수 항목이 누락되면 400 C-001을 반환한다")
    void rejectsInvalidRequest() throws Exception {
      UpdateOnboardingTagRequest request = new UpdateOnboardingTagRequest(
          null,
          ServiceType.MOBILE_APP,
          List.of(AgeBand.AGE_20S),
          CampaignObjective.CONVERSION,
          2_000_000L,
          10_000_000L,
          CampaignPeriod.M2_3
      );

      mockMvc.perform(put("/api/v1/onboarding/me/tags")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error.code").value("C-001"))
          .andExpect(jsonPath("$.error.fieldErrors[0].field").value("industry"));
    }
  }
}
