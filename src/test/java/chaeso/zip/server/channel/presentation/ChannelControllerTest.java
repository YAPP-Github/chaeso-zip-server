package chaeso.zip.server.channel.presentation;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.channel.application.ChannelService;
import chaeso.zip.server.channel.application.dto.AudienceMetricResponse;
import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.PricingResponse;
import chaeso.zip.server.channel.application.dto.ProductResponse;
import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.CurrencyType;
import chaeso.zip.server.channel.domain.vo.ExecutionType;
import chaeso.zip.server.channel.domain.vo.PriceType;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.channel.domain.vo.Vat;
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
@WebMvcTest(ChannelController.class)
class ChannelControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ChannelService channelService;

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
        null, null, null, null, null, List.of(pricing));
    ChannelDetailResponse detail = new ChannelDetailResponse(
        channelId, "11번가 광고", null, "요약", Category.SHOPPING_COMMERCE, "DISPLAY",
        List.of(), List.of(), null, null, null, null, List.of(), null, null,
        ExecutionType.SELF, List.of(), List.of(),
        List.of(product),
        List.of(new AudienceMetricResponse("MAU", new BigDecimal("1000000"), null, "명", "월")),
        List.of("전환율 개선 사례"));

    given(channelService.getChannel(channelId)).willReturn(detail);

    mockMvc.perform(get("/api/v1/channels/{id}", channelId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(channelId.toString()))
        .andExpect(jsonPath("$.data.primaryCategory").value("SHOPPING_COMMERCE"))
        .andExpect(jsonPath("$.data.executionType").value("SELF"))
        .andExpect(jsonPath("$.data.products[0].id").value(productId.toString()))
        .andExpect(jsonPath("$.data.products[0].pricing[0].pricingModel").value("CPM"))
        .andExpect(jsonPath("$.data.products[0].pricing[0].vat").value("EXCLUDED"))
        .andExpect(jsonPath("$.data.audienceMetrics[0].metricName").value("MAU"))
        .andExpect(jsonPath("$.data.references[0]").value("전환율 개선 사례"));
  }

  @Test
  @DisplayName("상품이 없는 채널은 products 를 빈 배열로 반환한다")
  void getChannel_emptyProducts() throws Exception {
    UUID channelId = UUID.randomUUID();
    ChannelDetailResponse detail = new ChannelDetailResponse(
        channelId, "상품없는 채널", null, null, Category.SHOPPING_COMMERCE, null,
        List.of(), List.of(), null, null, null, null, List.of(), null, null,
        null, List.of(), List.of(),
        List.of(), List.of(), List.of());

    given(channelService.getChannel(channelId)).willReturn(detail);

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
        .given(channelService).getChannel(channelId);

    mockMvc.perform(get("/api/v1/channels/{id}", channelId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("CH-001"));
  }
}
