package chaeso.zip.server.channel.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.channel.application.ChannelService;
import chaeso.zip.server.channel.application.dto.AudienceMetricResponse;
import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.channel.application.dto.PricingResponse;
import chaeso.zip.server.channel.application.dto.ProductResponse;
import chaeso.zip.server.channel.application.dto.RecommendationBasisResponse;
import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.CurrencyType;
import chaeso.zip.server.channel.domain.vo.ExecutionType;
import chaeso.zip.server.channel.domain.vo.PriceType;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.channel.domain.vo.Vat;
import chaeso.zip.server.common.exception.CommonErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import chaeso.zip.server.common.ratelimit.RateLimiter;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(ChannelController.class)
class ChannelControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ChannelService channelService;

  @MockitoBean
  private RateLimiter rateLimiter;

  @Test
  @DisplayName("쿼리 파라미터 없이 조회하면 페이지네이션 없이 전체 채널을 이름순으로 반환한다")
  void getChannels_noParams_returnsAll() throws Exception {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    List<ChannelListItemResponse> channels = List.of(
        channelListItem("11번가 광고"), channelListItem("네이버 GFA"), channelListItem("카카오모먼트"));
    given(channelService.getChannels(isNull(), isNull(), any(Pageable.class)))
        .willReturn(new PageImpl<>(channels, Pageable.unpaged(), channels.size()));

    mockMvc.perform(get("/api/v1/channels"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(3))
        .andExpect(jsonPath("$.data.number").value(0))
        .andExpect(jsonPath("$.data.size").value(3))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.first").value(true))
        .andExpect(jsonPath("$.data.last").value(true));

    verify(channelService).getChannels(isNull(), isNull(), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.isUnpaged()).isTrue();
    assertThat(pageable.getSort()).isEqualTo(Sort.by("name"));
  }

  @Test
  @DisplayName("primaryCategory 를 지정하면 업종을 그대로 조회에 넘긴다")
  void getChannels_withPrimaryCategory() throws Exception {
    given(channelService.getChannels(any(), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));

    mockMvc.perform(get("/api/v1/channels").param("primaryCategory", "SHOPPING_COMMERCE"))
        .andExpect(status().isOk());

    verify(channelService)
        .getChannels(isNull(), eq(List.of(Category.SHOPPING_COMMERCE)), any(Pageable.class));
  }

  @Test
  @DisplayName("primaryCategory 를 여러 번 넘기면 고른 업종을 순서대로 모두 넘긴다")
  void getChannels_withRepeatedPrimaryCategory() throws Exception {
    given(channelService.getChannels(any(), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));

    mockMvc.perform(get("/api/v1/channels")
            .param("primaryCategory", "SHOPPING_COMMERCE")
            .param("primaryCategory", "GAME"))
        .andExpect(status().isOk());

    verify(channelService).getChannels(isNull(),
        eq(List.of(Category.SHOPPING_COMMERCE, Category.GAME)), any(Pageable.class));
  }

  @Test
  @DisplayName("primaryCategory 를 쉼표로 이어 넘겨도 여러 업종으로 인식한다")
  void getChannels_withCommaSeparatedPrimaryCategory() throws Exception {
    given(channelService.getChannels(any(), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));

    mockMvc.perform(get("/api/v1/channels").param("primaryCategory", "SHOPPING_COMMERCE,GAME"))
        .andExpect(status().isOk());

    verify(channelService).getChannels(isNull(),
        eq(List.of(Category.SHOPPING_COMMERCE, Category.GAME)), any(Pageable.class));
  }

  @Test
  @DisplayName("채널명과 업종을 함께 주면 둘 다 조회에 넘긴다")
  void getChannels_withNameAndPrimaryCategory() throws Exception {
    given(channelService.getChannels(any(), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));

    mockMvc.perform(get("/api/v1/channels")
            .param("name", "11번가")
            .param("primaryCategory", "SHOPPING_COMMERCE"))
        .andExpect(status().isOk());

    verify(channelService).getChannels(
        eq("11번가"), eq(List.of(Category.SHOPPING_COMMERCE)), any(Pageable.class));
  }

  @Test
  @DisplayName("없는 업종 코드값을 주면 400 C-001 을 반환한다")
  void getChannels_invalidPrimaryCategory() throws Exception {
    mockMvc.perform(get("/api/v1/channels").param("primaryCategory", "NOT_A_CATEGORY"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(CommonErrorCode.INVALID_INPUT_VALUE.getCode()));
  }

  @Test
  @DisplayName("page 또는 size 를 지정하면 해당 값으로 페이지 조회한다 (생략된 값은 page=0, size=12)")
  void getChannels_withPagingParams() throws Exception {
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    given(channelService.getChannels(any(), any(), any(Pageable.class)))
        .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12, Sort.by("name")), 0));

    mockMvc.perform(get("/api/v1/channels").param("size", "5"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/channels").param("page", "2"))
        .andExpect(status().isOk());

    verify(channelService, times(2)).getChannels(isNull(), isNull(), pageableCaptor.capture());
    assertThat(pageableCaptor.getAllValues())
        .containsExactly(
            PageRequest.of(0, 5, Sort.by("name")),
            PageRequest.of(2, 12, Sort.by("name")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "101"})
  @DisplayName("size 가 1 미만이거나 상한(100)을 넘으면 400 과 공통 에러 포맷을 반환한다")
  void getChannels_invalidSize(String size) throws Exception {
    // 상한이 없으면 size=1000000 이 그대로 통과해 전체 채널을 한 응답에 직렬화한다
    mockMvc.perform(get("/api/v1/channels").param("size", size))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(CommonErrorCode.INVALID_INPUT_VALUE.getCode()));
  }

  @Test
  @DisplayName("size 가 상한과 같으면 통과한다")
  void getChannels_maxSize() throws Exception {
    given(channelService.getChannels(any(), any(), any(Pageable.class)))
        .willReturn(Page.empty());

    mockMvc.perform(get("/api/v1/channels").param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("채널 상세 조회가 성공하면 200 과 채널/상품/단가를 반환하고 enum 은 코드값으로 직렬화된다")
  void getChannel_success() throws Exception {
    UUID channelId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    PricingResponse pricing = new PricingResponse(
        PricingModel.CPM, new BigDecimal("3000"), null, "월", null, null,
        PriceType.LIST, Vat.EXCLUDED, CurrencyType.KRW, null);
    ProductResponse product = new ProductResponse(
        productId, "메인 배너", "DISPLAY", List.of(), 1_000_000, 5_000_000,
        1_500_000L, 5_250L, null, List.of(pricing), true);
    ChannelDetailResponse detail = new ChannelDetailResponse(
        channelId, "11번가 광고", "월 방문자 수 상위 오픈마켓", null, "요약",
        Category.SHOPPING_COMMERCE, "DISPLAY",
        List.of(), List.of(), null, null, null, null, List.of(), null, null,
        ExecutionType.SELF, List.of(), List.of(),
        List.of(product),
        List.of(new AudienceMetricResponse("MAU", new BigDecimal("1000000"), null, "명", "월")),
        List.of("전환율 개선 사례"), null, List.of("커머스 특화", "구매의도 타겟"));

    given(channelService.getChannel(channelId, null, null)).willReturn(detail);

    mockMvc.perform(get("/api/v1/channels/{id}", channelId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(channelId.toString()))
        .andExpect(jsonPath("$.data.tagline").value("월 방문자 수 상위 오픈마켓"))
        .andExpect(jsonPath("$.data.tags").value(contains("커머스 특화", "구매의도 타겟")))
        .andExpect(jsonPath("$.data.primaryCategory").value("SHOPPING_COMMERCE"))
        .andExpect(jsonPath("$.data.executionType").value("SELF"))
        .andExpect(jsonPath("$.data.products[0].id").value(productId.toString()))
        .andExpect(jsonPath("$.data.products[0].expectedClicks").value(5250))
        .andExpect(jsonPath("$.data.products[0].isExecutable").value(true))
        .andExpect(jsonPath("$.data.products[0].ctr").doesNotExist())
        .andExpect(jsonPath("$.data.iconUrl").value(nullValue()))
        .andExpect(jsonPath("$.data.products[0].pricing[0].valueMax").value(nullValue()))
        .andExpect(jsonPath("$.data.products[0].pricing[0].pricingModel").value("CPM"))
        .andExpect(jsonPath("$.data.products[0].pricing[0].vat").value("EXCLUDED"))
        .andExpect(jsonPath("$.data.audienceMetrics[0].metricName").value("MAU"))
        .andExpect(jsonPath("$.data.references[0]").value("전환율 개선 사례"))
        .andExpect(jsonPath("$.data.recommendationBasis").doesNotExist());
  }

  @Test
  @DisplayName("onboardingId 를 넘기면 그대로 전달하고 추천 근거를 함께 반환한다")
  void getChannel_withOnboardingId() throws Exception {
    UUID channelId = UUID.randomUUID();
    UUID onboardingId = UUID.randomUUID();
    RecommendationBasisResponse basis = new RecommendationBasisResponse(
        CampaignObjective.TRAFFIC, Category.SHOPPING_COMMERCE, 3_000_000L, 10_000_000L);
    given(channelService.getChannel(channelId, onboardingId, null))
        .willReturn(channelDetail(channelId, basis));

    mockMvc.perform(get("/api/v1/channels/{id}", channelId)
            .param("onboardingId", onboardingId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recommendationBasis.objective").value("TRAFFIC"))
        .andExpect(jsonPath("$.data.recommendationBasis.category").value("SHOPPING_COMMERCE"))
        .andExpect(jsonPath("$.data.recommendationBasis.budgetMin").value(3000000))
        .andExpect(jsonPath("$.data.recommendationBasis.budgetMax").value(10000000));

    verify(channelService).getChannel(channelId, onboardingId, null);
  }

  @Test
  @DisplayName("로그인한 요청은 조회자를 함께 넘겨 추천 근거의 소유자를 가릴 수 있게 한다")
  void getChannel_passesRequesterId() throws Exception {
    UUID channelId = UUID.randomUUID();
    UUID onboardingId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    given(channelService.getChannel(channelId, onboardingId, userId))
        .willReturn(channelDetail(channelId, null));

    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new UserPrincipal(userId), null, List.of()));
    try {
      mockMvc.perform(get("/api/v1/channels/{id}", channelId)
              .param("onboardingId", onboardingId.toString()))
          .andExpect(status().isOk());
    } finally {
      SecurityContextHolder.clearContext();
    }

    verify(channelService).getChannel(channelId, onboardingId, userId);
  }

  @Test
  @DisplayName("상품이 없는 채널은 products 를 빈 배열로 반환한다")
  void getChannel_emptyProducts() throws Exception {
    UUID channelId = UUID.randomUUID();
    ChannelDetailResponse detail = new ChannelDetailResponse(
        channelId, "상품없는 채널", null, null, "요약", Category.SHOPPING_COMMERCE, null,
        List.of(), List.of(), null, null, null, null, List.of(), null, null,
        null, List.of(), List.of(),
        List.of(), List.of(), List.of(), null, List.of());

    given(channelService.getChannel(channelId, null, null)).willReturn(detail);

    mockMvc.perform(get("/api/v1/channels/{id}", channelId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.products").isArray())
        .andExpect(jsonPath("$.data.products").isEmpty());
  }

  @Test
  @DisplayName("존재하지 않는 채널을 조회하면 404 와 공통 에러 포맷을 반환한다")
  void getChannel_notFound() throws Exception {
    UUID channelId = UUID.randomUUID();
    BDDMockito.willThrow(new ChannelNotFoundException(channelId))
        .given(channelService).getChannel(channelId, null, null);

    mockMvc.perform(get("/api/v1/channels/{id}", channelId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("CH-001"));
  }

  private static ChannelDetailResponse channelDetail(UUID channelId,
      RecommendationBasisResponse basis) {
    return new ChannelDetailResponse(
        channelId, "11번가 광고", null, null, null, Category.SHOPPING_COMMERCE, null,
        List.of(), List.of(), null, null, null, null, List.of(), null, null,
        null, List.of(), List.of(),
        List.of(), List.of(), List.of(), basis, List.of());
  }

  private ChannelListItemResponse channelListItem(String name) {
    return new ChannelListItemResponse(
        UUID.randomUUID(), name, null, null, Category.SHOPPING_COMMERCE);
  }
}
