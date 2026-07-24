package chaeso.zip.server.channel.application.dto;

import chaeso.zip.server.channel.domain.entity.ChannelAudienceMetric;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "채널 오디언스 규모 지표")
public record AudienceMetricResponse(
    @Schema(description = "지표명", example = "MAU", requiredMode = Schema.RequiredMode.REQUIRED)
    String metricName,
    @Schema(description = "지표 수치값", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    BigDecimal valueNumeric,
    @Schema(description = "지표 텍스트값", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String valueText,
    @Schema(description = "단위", example = "명", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String unit,
    @Schema(description = "집계 기간", example = "월", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String period) {

  public static AudienceMetricResponse from(ChannelAudienceMetric metric) {
    return new AudienceMetricResponse(
        metric.getMetricName(),
        metric.getValueNumeric(),
        metric.getValueText(),
        metric.getUnit(),
        metric.getPeriod());
  }
}
