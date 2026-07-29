package chaeso.zip.server.simulation.application.dto;

import chaeso.zip.server.estimation.domain.vo.ClickRange;
import chaeso.zip.server.estimation.domain.vo.ImpressionRange;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추정 범위")
public record CountRangeResponse(
    @Schema(description = "하한", example = "170000", requiredMode = Schema.RequiredMode.REQUIRED)
    long min,
    @Schema(description = "상한", example = "230000", requiredMode = Schema.RequiredMode.REQUIRED)
    long max) {

  public static CountRangeResponse from(ImpressionRange range) {
    return range == null ? null : new CountRangeResponse(range.min(), range.max());
  }

  public static CountRangeResponse from(ClickRange range) {
    return range == null ? null : new CountRangeResponse(range.min(), range.max());
  }

  public static CountRangeResponse of(Long min, Long max) {
    return min == null || max == null ? null : new CountRangeResponse(min, max);
  }

  /** 전체 합산에 쓰는 대표값. 범위의 중앙값을 반올림한다. */
  public long midpoint() {
    return Math.round((min + max) / 2.0);
  }
}
