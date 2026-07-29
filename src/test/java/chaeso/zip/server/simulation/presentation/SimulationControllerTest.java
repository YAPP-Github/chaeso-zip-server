package chaeso.zip.server.simulation.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.simulation.application.SimulationService;
import chaeso.zip.server.simulation.application.dto.CountRangeResponse;
import chaeso.zip.server.simulation.application.dto.SimulationCommand;
import chaeso.zip.server.simulation.application.dto.SimulationItemResponse;
import chaeso.zip.server.simulation.application.dto.SimulationResponse;
import chaeso.zip.server.simulation.domain.BasisNote;
import chaeso.zip.server.simulation.domain.vo.SimPeriod;
import chaeso.zip.server.simulation.presentation.dto.AllocationRequest;
import chaeso.zip.server.simulation.presentation.dto.SimulationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(SimulationController.class)
class SimulationControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID CHANNEL_ID = UUID.randomUUID();
  private static final UUID PRODUCT_ID = UUID.randomUUID();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private SimulationService simulationService;

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
  @DisplayName("계산 요청이 성공하면 200 과 매체별 추정치를 반환하고 simulationId 는 응답에 없다")
  void estimateReturnsResultWithoutSimulationId() throws Exception {
    given(simulationService.estimate(any(SimulationCommand.class))).willReturn(response(null));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request(3_000_000, SimPeriod.M1))))
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
            .content(objectMapper.writeValueAsString(request(3_000_000, SimPeriod.M1))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.simulationId").value(simulationId.toString()));
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

  @ParameterizedTest
  @ValueSource(ints = {99_999, 5_000_001})
  @DisplayName("총 예산이 10만~500만 범위를 벗어나면 400 C-001 과 필드 에러를 반환한다")
  void rejectsBudgetOutOfRange(int totalBudgetWon) throws Exception {
    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request(totalBudgetWon, SimPeriod.M1))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("totalBudgetWon"));
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
    SimulationRequest request = new SimulationRequest(3_000_000, SimPeriod.M1, List.of());

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
    SimulationRequest request = new SimulationRequest(3_000_000, SimPeriod.M1,
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
    SimulationRequest request = new SimulationRequest(3_000_000, SimPeriod.M1,
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
    SimulationRequest request = new SimulationRequest(3_000_000, SimPeriod.M1, List.of(
        new AllocationRequest(CHANNEL_ID, 2_000_000, new BigDecimal("67")),
        new AllocationRequest(CHANNEL_ID, 1_000_000, new BigDecimal("33"))));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].reason")
            .value("같은 채널에 예산을 두 번 배분할 수 없습니다"));
  }

  @Test
  @DisplayName("배분 예산의 합이 총 예산을 넘으면 400 C-001 을 반환한다")
  void rejectsAllocationsExceedingTotalBudget() throws Exception {
    SimulationRequest request = new SimulationRequest(1_000_000, SimPeriod.M1, List.of(
        new AllocationRequest(CHANNEL_ID, 600_000, new BigDecimal("50")),
        new AllocationRequest(UUID.randomUUID(), 600_000, new BigDecimal("50"))));

    mockMvc.perform(post("/api/v1/simulations/estimate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].reason")
            .value("배분한 예산의 합이 총 예산을 넘을 수 없습니다"));
  }

  @Test
  @DisplayName("총 예산을 다 나누지 않은 배분은 허용한다")
  void allowsPartiallyAllocatedBudget() throws Exception {
    // 배분을 마치지 않은 정상 단계로 본다
    given(simulationService.estimate(any(SimulationCommand.class))).willReturn(response(null));
    SimulationRequest request = new SimulationRequest(5_000_000, SimPeriod.M1,
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
            .content(objectMapper.writeValueAsString(request(3_000_000, SimPeriod.M1))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("CH-001"));
  }

  private static SimulationRequest request(int totalBudgetWon, SimPeriod period) {
    return new SimulationRequest(totalBudgetWon, period,
        List.of(new AllocationRequest(CHANNEL_ID, 3_000_000, new BigDecimal("100"))));
  }

  private static SimulationResponse response(UUID simulationId) {
    SimulationItemResponse item = new SimulationItemResponse(
        CHANNEL_ID, "11번가 광고", PRODUCT_ID, 3_000_000L, new BigDecimal("100"),
        new CountRangeResponse(850_000, 1_150_000), new CountRangeResponse(21_250, 28_750),
        new BigDecimal("120"), new BigDecimal("3000"), true, null, BasisNote.COMMON);
    return new SimulationResponse(simulationId, 3_000_000L, SimPeriod.M1, 1_000_000L, 25_000L,
        1, List.of(item));
  }
}
