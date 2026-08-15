package chaeso.zip.server.comparison.application.dto;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.estimation.domain.vo.ClickRange;
import chaeso.zip.server.estimation.domain.vo.ImpressionRange;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "채널별 비교 항목")
public record ChannelComparisonItemResponse(
    @Schema(description = "채널 식별자", example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID channelId,
    @Schema(description = "채널명", example = "11번가 광고", requiredMode = Schema.RequiredMode.REQUIRED)
    String channelName,
    @Schema(description = "채널의 주요 오디언스. 등록된 정보가 없으면 null",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String audienceSummary,
    @Schema(description = "지원 광고 형태. 등록된 정보가 없으면 빈 배열",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> adFormats,
    @Schema(description = "지원 타기팅 방식. 등록된 정보가 없으면 빈 배열",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> targetingMethods,
    @Schema(description = "최소 광고비(원). 등록된 정보가 없으면 null",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Integer minBudgetWon,
    @Schema(description = "채널 장점. 등록된 정보가 없으면 빈 배열",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> advantages,
    @Schema(description = """
        채널 인사이트 태그. 온보딩이 없으면 기본 태그 전체, 있으면 조건과 일치한 \
        CATEGORY, OBJECTIVE, AGE_BAND 중 최대 2개를 반환한다. 없으면 빈 배열""",
        example = "[\"CATEGORY\", \"OBJECTIVE\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> tags,
    @Schema(description = """
        클릭당 비용(원). 클릭당 과금 매체는 대표 단가 그대로, 그 외 매체는 \
        온보딩 예산 / 예상 클릭 수(중앙값)로 환산한다. 환산할 수 없으면 null""",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    BigDecimal cpcWon,
    @Schema(description = "1,000회 노출당 단가(원). 대표 단가가 CPM일 때만 채워진다",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    BigDecimal cpmWon,
    @Schema(description = "온보딩 조건과의 적합도(%). 로그인 뒤 온보딩이 있을 때만 반환",
        example = "78",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Integer matchRate,
    @Schema(description = """
        예상 노출 수 범위. 온보딩이 없으면 기본 값(100만원, 1개월)로 추정 \
        예산 부족 또는 추정 불가 시 null""",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    CountRangeResponse estImpressions,
    @Schema(description = """
        예상 클릭 수 범위. 온보딩이 없으면 기본 값(100만원, 1개월)로 추정 \
        예산 부족 또는 추정 불가 시 null""",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    CountRangeResponse estClicks) {

  /** 온보딩 없이 채널 정보와 대표 단가만 비교한 항목. */
  public static ChannelComparisonItemResponse from(Channel channel, BigDecimal cpcWon,
      BigDecimal cpmWon) {
    return new ChannelComparisonItemResponse(
        channel.getId(),
        channel.getName(),
        channel.getAudienceSummary(),
        emptyIfNull(channel.getAdFormats()),
        emptyIfNull(channel.getTargetingMethods()),
        channel.getMinBudgetWon(),
        emptyIfNull(channel.getAdvantages()),
        emptyIfNull(channel.getDefaultTags()),
        cpcWon,
        cpmWon,
        null,
        null,
        null);
  }

  /** 온보딩 조건으로 태그, 단가, 적합도, 추정을 채운 항목. */
  public static ChannelComparisonItemResponse from(Channel channel, List<String> tags,
      Integer matchRate, BigDecimal cpcWon, BigDecimal cpmWon, ImpressionRange impressions,
      ClickRange clicks) {
    return new ChannelComparisonItemResponse(
        channel.getId(),
        channel.getName(),
        channel.getAudienceSummary(),
        emptyIfNull(channel.getAdFormats()),
        emptyIfNull(channel.getTargetingMethods()),
        channel.getMinBudgetWon(),
        emptyIfNull(channel.getAdvantages()),
        emptyIfNull(tags),
        cpcWon,
        cpmWon,
        matchRate,
        CountRangeResponse.from(impressions),
        CountRangeResponse.from(clicks));
  }

  /** 비로그인 비교용. 단가·장점·최소광고비·태그만 남긴다. */
  public ChannelComparisonItemResponse hideCatalogDetails() {
    return new ChannelComparisonItemResponse(
        channelId,
        channelName,
        null,
        List.of(),
        List.of(),
        minBudgetWon,
        advantages,
        tags,
        cpcWon,
        cpmWon,
        null,
        null,
        null);
  }

  /** 목록은 값이 없어도 null 대신 빈 배열로 반환한다. */
  private static List<String> emptyIfNull(List<String> values) {
    return values == null ? List.of() : values;
  }
}
