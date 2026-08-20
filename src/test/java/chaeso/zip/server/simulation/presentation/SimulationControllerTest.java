package chaeso.zip.server.simulation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.simulation.application.SimulationService;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.simulation.application.dto.SimulationCommand;
import chaeso.zip.server.simulation.application.dto.SimulationItemResponse;
import chaeso.zip.server.simulation.application.dto.SimulationResponse;
import chaeso.zip.server.simulation.application.dto.SimulationSummaryResponse;
import chaeso.zip.server.simulation.domain.BasisNote;
import chaeso.zip.server.simulation.domain.SimulationNotFoundException;
import chaeso.zip.server.simulation.presentation.dto.AllocationRequest;
import chaeso.zip.server.simulation.presentation.dto.SaveSimulationRequest;
import chaeso.zip.server.simulation.presentation.dto.SimulationRequest;
import chaeso.zip.server.support.security.WithUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import chaeso.zip.server.common.ratelimit.RateLimiter;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(SimulationController.class)
@WithUserPrincipal
class SimulationControllerTest {

  private static final UUID USER_ID = UUID.fromString(WithUserPrincipal.DEFAULT_USER_ID);
  private static final UUID CHANNEL_ID = UUID.randomUUID();
  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final String SERVICE_NAME = "채소집";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private SimulationService simulationService;

  @MockitoBean
  private RateLimiter rateLimiter;

  @Test
  @DisplayName("계산 요청이 성공하면 200 과 매체별 추정치를 반환하고 simulationId 는 응답에 없다")
  void estimateReturnsResultWithoutSimulationId() throws Exception {
    given(simulationService.estimate(any(SimulationCommand.class))).willReturn(response(null));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request(3_000_000, CampaignPeriod.M1))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.simulationId").doesNotExist())
        .andExpect(jsonPath("$.data.period").value("M1"))
        .andExpect(jsonPath("$.data.totalEstImpressions").value(1_000_000))
        .andExpect(jsonPath("$.data.totalEstClicks").value(25_000))
        .andExpect(jsonPath("$.data.items[0].channelId").value(CHANNEL_ID.toString()))
        .andExpect(jsonPath("$.data.items[0].channelName").value("11번가 광고"))
        .andExpect(jsonPath("$.data.items[0].estImpressions.min").value(850_000))
        .andExpect(jsonPath("$.data.items[0].estImpressions.max").value(1_150_000))
        .andExpect(jsonPath("$.data.executableChannelCount").value(1))
        .andExpect(jsonPath("$.data.items[0].cpcWon").value(120))
        .andExpect(jsonPath("$.data.items[0].isExecutable").value(true))
        .andExpect(jsonPath("$.data.items[0].basisNote").value(BasisNote.COMMON));
  }

  @Test
  @DisplayName("저장 요청이 성공하면 201 과 simulationId 를 반환한다")
  void saveReturnsCreatedWithSimulationId() throws Exception {
    UUID simulationId = UUID.randomUUID();
    given(simulationService.save(eq(USER_ID), any(SimulationCommand.class)))
        .willReturn(response(simulationId));

    mockMvc.perform(post("/api/v1/simulations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(saveRequest(3_000_000))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.simulationId").value(simulationId.toString()));

    ArgumentCaptor<SimulationCommand> command = ArgumentCaptor.forClass(SimulationCommand.class);
    verify(simulationService).save(eq(USER_ID), command.capture());
    assertThat(command.getValue().serviceName()).isEqualTo(SERVICE_NAME);
  }

  @Test
  @DisplayName("저장은 서비스명이 없으면 400 C-001 과 필드 에러를 반환한다")
  void saveRejectsMissingServiceName() throws Exception {
    SaveSimulationRequest request = new SaveSimulationRequest(null, 3_000_000, CampaignPeriod.M1,
        List.of(new AllocationRequest(CHANNEL_ID, 3_000_000, new BigDecimal("100"))));

    mockMvc.perform(post("/api/v1/simulations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("serviceName"));
  }

  @Test
  @DisplayName("저장도 계산과 같은 배분 규칙을 받는다")
  void saveKeepsAllocationRules() throws Exception {
    // 요청을 나누면서 규칙이 한쪽에만 남는 일이 없어야 한다
    SaveSimulationRequest request = new SaveSimulationRequest(SERVICE_NAME, 1_000_000,
        CampaignPeriod.M1,
        List.of(new AllocationRequest(CHANNEL_ID, 2_000_000, new BigDecimal("100"))));

    mockMvc.perform(post("/api/v1/simulations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[*].field")
            .value(hasItem("withinTotalBudget")));
  }

  @Test
  @DisplayName("계산은 남길 곳이 없어 서비스명을 받지 않는다")
  void estimateTakesNoServiceName() throws Exception {
    // 비로그인도 부르는 공개 경로라, 저장에 필요한 값을 여기까지 요구하면 기존 호출이 깨진다
    given(simulationService.estimate(any(SimulationCommand.class))).willReturn(response(null));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request(3_000_000, CampaignPeriod.M1))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    ArgumentCaptor<SimulationCommand> command = ArgumentCaptor.forClass(SimulationCommand.class);
    verify(simulationService).estimate(command.capture());
    assertThat(command.getValue().serviceName()).isNull();
  }

  @Test
  @DisplayName("불러오기에 저장된 결과가 있으면 200 과 스냅샷을 반환한다")
  void latestReturnsSavedSnapshot() throws Exception {
    UUID simulationId = UUID.randomUUID();
    given(simulationService.findLatest(USER_ID)).willReturn(Optional.of(response(simulationId)));

    mockMvc.perform(get("/api/v1/simulations/latest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.simulationId").value(simulationId.toString()))
        .andExpect(jsonPath("$.data.items[0].channelProductId").value(PRODUCT_ID.toString()));
  }

  @Test
  @DisplayName("불러오기에 저장된 결과가 없으면 204 를 반환한다")
  void latestReturnsNoContentWhenNothingSaved() throws Exception {
    given(simulationService.findLatest(USER_ID)).willReturn(Optional.empty());

    mockMvc.perform(get("/api/v1/simulations/latest"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("내 목록은 200 과 페이지 요약을 반환한다")
  void listReturnsSummaryPage() throws Exception {
    UUID simulationId = UUID.randomUUID();
    given(simulationService.findMySimulations(eq(USER_ID), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(summary(simulationId)), PageRequest.of(0, 10), 1));

    mockMvc.perform(get("/api/v1/simulations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].id").value(simulationId.toString()))
        .andExpect(jsonPath("$.data.content[0].serviceName").value(SERVICE_NAME))
        .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-03-14T10:22:31"))
        .andExpect(jsonPath("$.data.content[0].channelNames[0]").value("11번가 광고"))
        .andExpect(jsonPath("$.data.content[0].items").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].totalBudgetWon").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].period").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].totalEstImpressions").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].totalEstClicks").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].channelCount").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].executableChannelCount").doesNotExist())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.size").value(10));
  }

  @Test
  @DisplayName("page/size 를 생략하면 0 페이지 5건으로 조회한다")
  void listDefaultsToFirstPageOfFive() throws Exception {
    given(simulationService.findMySimulations(eq(USER_ID), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));

    mockMvc.perform(get("/api/v1/simulations"))
        .andExpect(status().isOk());

    verify(simulationService).findMySimulations(USER_ID, PageRequest.of(0, 5));
  }

  @Test
  @DisplayName("요청한 page/size 를 그대로 조회에 넘긴다")
  void listPassesRequestedPageAndSize() throws Exception {
    given(simulationService.findMySimulations(eq(USER_ID), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

    mockMvc.perform(get("/api/v1/simulations").param("page", "2").param("size", "5"))
        .andExpect(status().isOk());

    verify(simulationService).findMySimulations(USER_ID, PageRequest.of(2, 5));
  }

  @Test
  @DisplayName("page/size 가 허용 범위를 벗어나면 400 C-001 을 반환한다")
  void listRejectsPageSizeOutOfRange() throws Exception {
    mockMvc.perform(get("/api/v1/simulations").param("size", "51"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("C-001"));
  }

  @Test
  @DisplayName("상세 조회는 200 과 매체별 항목까지 담은 스냅샷을 반환한다")
  void detailReturnsSnapshotWithItems() throws Exception {
    UUID simulationId = UUID.randomUUID();
    given(simulationService.findSimulation(USER_ID, simulationId))
        .willReturn(response(simulationId));

    mockMvc.perform(get("/api/v1/simulations/{simulationId}", simulationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.simulationId").value(simulationId.toString()))
        .andExpect(jsonPath("$.data.executableChannelCount").value(1))
        .andExpect(jsonPath("$.data.items[0].channelName").value("11번가 광고"))
        .andExpect(jsonPath("$.data.items[0].estClicks.min").value(21_250))
        // 값이 없는 선택 필드는 null 로 담지 않고 생략한다. 스키마의 NOT_REQUIRED 와 같은 계약
        .andExpect(jsonPath("$.data.items[0].shortfallWon").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].basisNote").value(BasisNote.COMMON));
  }

  @Test
  @DisplayName("없는 id 나 남의 시뮬레이션을 상세 조회하면 404 SIM-001 을 반환한다")
  void detailReturnsNotFound() throws Exception {
    UUID simulationId = UUID.randomUUID();
    willThrow(new SimulationNotFoundException(simulationId))
        .given(simulationService).findSimulation(USER_ID, simulationId);

    mockMvc.perform(get("/api/v1/simulations/{simulationId}", simulationId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("SIM-001"));
  }

  @Test
  @DisplayName("상세 경로가 /latest 를 가로채지 않는다")
  void detailDoesNotShadowLatest() throws Exception {
    given(simulationService.findLatest(USER_ID))
        .willReturn(Optional.of(response(UUID.randomUUID())));

    mockMvc.perform(get("/api/v1/simulations/latest"))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(ints = {99_999, 10_000_001})
  @DisplayName("총 예산이 10만~1,000만 범위를 벗어나면 400 C-001 과 필드 에러를 반환한다")
  void rejectsBudgetOutOfRange(int totalBudgetWon) throws Exception {
    // 배분은 두 총 예산 모두에 들어가는 금액으로 둔다. 넘치면 배분 합계 검증까지 함께 위반되고,
    // 검증 실행 순서는 보장되지 않아 어느 필드가 먼저 담길지 알 수 없다
    SimulationRequest request = new SimulationRequest(totalBudgetWon, CampaignPeriod.M1,
        List.of(new AllocationRequest(CHANNEL_ID, 50_000, new BigDecimal("100"))));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[*].field").value(hasItem("totalBudgetWon")));
  }

  @Test
  @DisplayName("총 예산이 상한과 같으면 통과한다")
  void allowsBudgetAtUpperBound() throws Exception {
    given(simulationService.estimate(any(SimulationCommand.class))).willReturn(response(null));
    SimulationRequest request = new SimulationRequest(10_000_000, CampaignPeriod.M1,
        List.of(new AllocationRequest(CHANNEL_ID, 10_000_000, new BigDecimal("100"))));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("집행 기간이 없으면 400 C-001 을 반환한다")
  void rejectsMissingPeriod() throws Exception {
    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request(3_000_000, null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("period"));
  }

  @Test
  @DisplayName("배분 목록이 비어 있으면 400 C-001 을 반환한다")
  void rejectsEmptyAllocations() throws Exception {
    SimulationRequest request = new SimulationRequest(3_000_000, CampaignPeriod.M1, List.of());

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("allocations"));
  }

  @Test
  @DisplayName("배분 비율이 100 을 넘으면 400 C-001 을 반환한다")
  void rejectsAllocationPctOverHundred() throws Exception {
    SimulationRequest request = new SimulationRequest(3_000_000, CampaignPeriod.M1,
        List.of(new AllocationRequest(CHANNEL_ID, 3_000_000, new BigDecimal("101"))));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("allocations[0].allocationPct"));
  }

  @Test
  @DisplayName("배분 예산이 음수면 400 C-001 을 반환한다")
  void rejectsNegativeAllocatedBudget() throws Exception {
    SimulationRequest request = new SimulationRequest(3_000_000, CampaignPeriod.M1,
        List.of(new AllocationRequest(CHANNEL_ID, -1, new BigDecimal("100"))));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("allocations[0].budgetWon"));
  }

  @Test
  @DisplayName("같은 채널에 두 번 배분하면 400 C-001 을 반환한다")
  void rejectsDuplicateChannel() throws Exception {
    // 그대로 통과시키면 그 채널이 두 항목으로 계산되어 노출·클릭 합계가 이중 계산된다
    SimulationRequest request = new SimulationRequest(3_000_000, CampaignPeriod.M1, List.of(
        new AllocationRequest(CHANNEL_ID, 2_000_000, new BigDecimal("67")),
        new AllocationRequest(CHANNEL_ID, 1_000_000, new BigDecimal("33"))));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[*].reason")
            .value(hasItem("같은 채널에 예산을 두 번 배분할 수 없습니다")));
  }

  @Test
  @DisplayName("배분 예산의 합이 총 예산을 넘으면 400 C-001 을 반환한다")
  void rejectsAllocationsExceedingTotalBudget() throws Exception {
    SimulationRequest request = new SimulationRequest(1_000_000, CampaignPeriod.M1, List.of(
        new AllocationRequest(CHANNEL_ID, 600_000, new BigDecimal("50")),
        new AllocationRequest(UUID.randomUUID(), 600_000, new BigDecimal("50"))));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[*].reason")
            .value(hasItem("배분한 예산의 합이 총 예산을 넘을 수 없습니다")));
  }

  @Test
  @DisplayName("총 예산을 다 나누지 않은 배분은 허용한다")
  void allowsPartiallyAllocatedBudget() throws Exception {
    // 배분을 마치지 않은 정상 단계로 본다
    given(simulationService.estimate(any(SimulationCommand.class))).willReturn(response(null));
    SimulationRequest request = new SimulationRequest(5_000_000, CampaignPeriod.M1,
        List.of(new AllocationRequest(CHANNEL_ID, 1_000_000, new BigDecimal("20"))));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("존재하지 않는 채널을 배분하면 404 CH-001 을 반환한다")
  void rejectsUnknownChannel() throws Exception {
    willThrow(new ChannelNotFoundException(CHANNEL_ID))
        .given(simulationService).estimate(any(SimulationCommand.class));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request(3_000_000, CampaignPeriod.M1))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("CH-001"));
  }

  private static SimulationRequest request(int totalBudgetWon, CampaignPeriod period) {
    return new SimulationRequest(totalBudgetWon, period,
        List.of(new AllocationRequest(CHANNEL_ID, 3_000_000, new BigDecimal("100"))));
  }

  private static SaveSimulationRequest saveRequest(int totalBudgetWon) {
    return new SaveSimulationRequest(SERVICE_NAME, totalBudgetWon, CampaignPeriod.M1,
        List.of(new AllocationRequest(CHANNEL_ID, 3_000_000, new BigDecimal("100"))));
  }

  private static SimulationSummaryResponse summary(UUID simulationId) {
    return new SimulationSummaryResponse(simulationId, SERVICE_NAME,
        LocalDateTime.of(2026, 3, 14, 10, 22, 31), List.of("11번가 광고", "당근마켓 광고"));
  }

  private static SimulationResponse response(UUID simulationId) {
    SimulationItemResponse item = new SimulationItemResponse(
        CHANNEL_ID, "11번가 광고", null, PRODUCT_ID, 3_000_000L, new BigDecimal("100"),
        new CountRangeResponse(850_000, 1_150_000), new CountRangeResponse(21_250, 28_750),
        new BigDecimal("120"), new BigDecimal("3000"), 1_000_000L, true, null, BasisNote.COMMON);
    return new SimulationResponse(simulationId, 3_000_000L, CampaignPeriod.M1, 1_000_000L, 25_000L,
        1, List.of(item));
  }
}
