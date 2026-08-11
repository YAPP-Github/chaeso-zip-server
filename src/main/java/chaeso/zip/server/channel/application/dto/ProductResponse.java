package chaeso.zip.server.channel.application.dto;

import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "채널 광고 상품")
public record ProductResponse(
    @Schema(description = "상품 식별자", example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,
    @Schema(description = "상품명", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String productName,
    @Schema(description = "인벤토리 유형", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String inventoryType,
    @Schema(description = "지원 광고 목표 코드값 목록(없으면 빈 배열)",
        example = "[\"AWARENESS\", \"TRAFFIC\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    List<CampaignObjective> supportedObjectives,
    @Schema(description = "최소 예산(원)", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Integer minBudgetWon,
    @Schema(description = "최대 예산(원)", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Integer maxBudgetWon,
    @Schema(description = "예상 노출수", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Long expectedImpressions,
    @Schema(description = "예상 클릭수(예상 노출수 × CTR)",
        example = "5250", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Long expectedClicks,
    @Schema(description = "예상 집행 기간", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String expectedPeriod,
    @Schema(description = "상품 단가 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    List<PricingResponse> pricing) {

  public static ProductResponse from(ChannelProduct product, List<PricingResponse> pricing,
      Long expectedClicks) {
    return new ProductResponse(
        product.getId(),
        product.getProductName(),
        product.getInventoryType(),
        emptyIfNull(product.getSupportedObjectives()),
        product.getMinBudgetWon(),
        product.getMaxBudgetWon(),
        product.getExpectedImpressions(),
        expectedClicks,
        product.getExpectedPeriod(),
        pricing);
  }

  /** 목록은 값이 없어도 null 대신 빈 배열로 준다. */
  private static <T> List<T> emptyIfNull(List<T> values) {
    return values == null ? List.of() : values;
  }
}
