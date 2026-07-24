package chaeso.zip.server.onboarding.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.application.OnboardingService;
import chaeso.zip.server.onboarding.application.dto.OnboardingSubmitResponse;
import chaeso.zip.server.onboarding.application.dto.SubmitOnboardingCommand;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import chaeso.zip.server.onboarding.presentation.dto.SubmitOnboardingRequest;
import chaeso.zip.server.support.OnboardingFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(OnboardingController.class)
class OnboardingControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private OnboardingService onboardingService;

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
  @DisplayName("온보딩 제출에 성공하면 201과 onboardingId, createdAt을 반환한다")
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
  @DisplayName("연령대가 비어 있으면 400 C-001과 필드 에러를 반환한다")
  void rejectsEmptyAgeBands() throws Exception {
    SubmitOnboardingRequest request = new SubmitOnboardingRequest(
        "채소집", Category.SHOPPING_COMMERCE, ServiceType.WEB, List.of(),
        CampaignObjective.TRAFFIC, 1L, 2L, CampaignPeriod.M1, AdExperience.NONE, List.of());

    mockMvc.perform(post("/api/v1/onboarding")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("targetAgeBands"));
  }

  @Test
  @DisplayName("서비스가 도메인 예외를 던지면 GlobalExceptionHandler가 매핑한 상태/코드로 전파된다")
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
