package chaeso.zip.server.channel.application.dto;

import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.vo.CurrencyType;
import chaeso.zip.server.channel.domain.vo.PriceType;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.channel.domain.vo.Vat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "상품 단가")
public record PricingResponse(
    @Schema(description = "과금 모델 코드값", example = "CPM", requiredMode = Schema.RequiredMode.REQUIRED)
    PricingModel pricingModel,
    @Schema(description = "단가 값", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    BigDecimal value,
    @Schema(description = "단가 상한값(구간형 단가)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    BigDecimal valueMax,
    @Schema(description = "단가 적용 단위 기간", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String unitPeriod,
    @Schema(description = "단가 적용 단위 일수", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    BigDecimal unitDays,
    @Schema(description = "단가 적용 세그먼트", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String segment,
    @Schema(description = "가격 유형 코드값", example = "LIST", requiredMode = Schema.RequiredMode.REQUIRED)
    PriceType priceType,
    @Schema(description = "부가세 포함 여부 코드값", example = "EXCLUDED",
        requiredMode = Schema.RequiredMode.REQUIRED)
    Vat vat,
    @Schema(description = "통화 코드값", example = "KRW", requiredMode = Schema.RequiredMode.REQUIRED)
    CurrencyType currency,
    @Schema(description = "단가 유효 기간", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String validPeriod) {

  public static PricingResponse from(ChannelPricing pricing) {
    return new PricingResponse(
        pricing.getPricingModel(),
        pricing.getValue(),
        pricing.getValueMax(),
        pricing.getUnitPeriod(),
        pricing.getUnitDays(),
        pricing.getSegment(),
        pricing.getPriceType(),
        pricing.getVat(),
        pricing.getCurrency(),
        pricing.getValidPeriod());
  }
}
