package chaeso.zip.server.comparison.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.common.ratelimit.RateLimiter;
import chaeso.zip.server.comparison.application.ChannelComparisonService;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonItemResponse;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonResponse;
import chaeso.zip.server.support.security.WithUserPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(ChannelComparisonController.class)
class ChannelComparisonControllerTest {

  private static final UUID USER_ID = UUID.fromString(WithUserPrincipal.DEFAULT_USER_ID);

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ChannelComparisonService channelComparisonService;

  @MockitoBean
  private RateLimiter rateLimiter;

  @Test
  @DisplayName("비교할 채널을 선택하지 않으면 400 으로 거부한다")
  void rejectsMissingChannelIds() throws Exception {
    mockMvc.perform(get("/api/v1/channel-comparisons"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("channelIds"))
        .andExpect(jsonPath("$.error.fieldErrors[0].reason")
            .value("비교할 채널을 1개 이상 선택해 주세요"));
  }

  @Test
  @DisplayName("한 번에 채널을 4개 이상 비교하면 400 으로 거부한다")
  void rejectsMoreThanThreeChannels() throws Exception {
    String csv = String.join(",", UUID.randomUUID().toString(), UUID.randomUUID().toString(),
        UUID.randomUUID().toString(), UUID.randomUUID().toString());

    mockMvc.perform(get("/api/v1/channel-comparisons").param("channelIds", csv))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].reason")
            .value("비교할 채널은 최대 3개까지 선택할 수 있습니다"));
  }

  @Test
  @DisplayName("같은 채널을 중복해서 선택하면 400 으로 거부한다")
  void rejectsDuplicateChannels() throws Exception {
    UUID channelId = UUID.randomUUID();

    mockMvc.perform(get("/api/v1/channel-comparisons")
            .param("channelIds", channelId + "," + channelId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].reason")
            .value("같은 채널을 중복해서 비교할 수 없습니다"));
  }

  @Test
  @DisplayName("채널을 1개부터 3개까지 선택하면 로그인 없이 비교할 수 있다")
  void allowsAnonymousComparison() throws Exception {
    UUID channelId = UUID.randomUUID();
    given(channelComparisonService.compare(any(), any(), any()))
        .willReturn(ChannelComparisonResponse.of(List.of()));

    mockMvc.perform(get("/api/v1/channel-comparisons")
            .param("channelIds", channelId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("비로그인 비교는 요청자 정보 없이 처리한다")
  void passesNoRequesterForAnonymousComparison() throws Exception {
    UUID channelId = UUID.randomUUID();
    given(channelComparisonService.compare(any(), any(), any()))
        .willReturn(ChannelComparisonResponse.of(List.of()));

    mockMvc.perform(get("/api/v1/channel-comparisons")
            .param("channelIds", channelId.toString()))
        .andExpect(status().isOk());

    ArgumentCaptor<List<UUID>> channelIdsCaptor = ArgumentCaptor.captor();
    verify(channelComparisonService)
        .compare(channelIdsCaptor.capture(), isNull(), isNull());
    assertThat(channelIdsCaptor.getValue()).containsExactly(channelId);
  }

  @Test
  @WithUserPrincipal
  @DisplayName("로그인 비교는 사용자와 온보딩 식별자를 맞춤 비교에 전달한다")
  void passesUserAndOnboardingForAuthenticatedComparison() throws Exception {
    UUID channelId1 = UUID.randomUUID();
    UUID channelId2 = UUID.randomUUID();
    UUID onboardingId = UUID.randomUUID();
    ChannelComparisonItemResponse item = ChannelComparisonItemResponse.from(
        chaeso.zip.server.support.ChannelCatalogFixture.channel(UUID.randomUUID(), "11번가 광고"),
        null, null);
    given(channelComparisonService.compare(any(), any(), any()))
        .willReturn(ChannelComparisonResponse.of(List.of(item)));

    mockMvc.perform(get("/api/v1/channel-comparisons")
            .param("channelIds", channelId1 + "," + channelId2)
            .param("onboardingId", onboardingId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].channelId").value(item.channelId().toString()))
        .andExpect(jsonPath("$.data.items[0].displayPlatforms").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].executionType").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].isExecutable").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].shortfallWon").doesNotExist());

    ArgumentCaptor<List<UUID>> channelIdsCaptor = ArgumentCaptor.captor();
    verify(channelComparisonService)
        .compare(channelIdsCaptor.capture(), eq(onboardingId), eq(USER_ID));
    assertThat(channelIdsCaptor.getValue()).containsExactly(channelId1, channelId2);
  }
}
