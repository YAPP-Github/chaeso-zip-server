package chaeso.zip.server.comparison.application.dto;

import chaeso.zip.server.comparison.domain.ChannelComparisonSnapshot;
import chaeso.zip.server.comparison.domain.entity.ChannelComparisonItem;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
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
    @Schema(description = "채널 로고 미리보기 URL",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String previewImageUrl,
    @Schema(description = "채널의 주요 오디언스. 등록된 정보가 없으면 null, 비로그인이면 MOCK 값",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String audienceSummary,
    @Schema(description = "지원 광고 형태. 등록된 정보가 없으면 빈 배열, 비로그인이면 MOCK 값",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> adFormats,
    @Schema(description = "지원 타기팅 방식. 등록된 정보가 없으면 빈 배열, 비로그인이면 MOCK 값",
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
    @Schema(description = "온보딩 조건과의 적합도(%). 로그인 뒤 온보딩이 있을 때만 계산값, 비로그인이면 MOCK 값",
        example = "78",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Integer matchRate,
    @Schema(description = """
        예상 노출 수 범위. 로그인했고 온보딩이 없으면 기본 값(100만원, 1개월) 기준, \
        예산 부족 또는 추정 불가 시 null. \
        비로그인이면 MOCK 값""",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    CountRangeResponse estImpressions,
    @Schema(description = """
        예상 클릭 수 범위. 로그인했고 온보딩이 없으면 기본 값(100만원, 1개월) 기준,
        예산 부족 또는 추정 불가 시 null. \
        비로그인이면 MOCK 값""",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    CountRangeResponse estClicks) {

  /** 비교 스냅샷을 응답 항목으로 변환한다. */
  public static ChannelComparisonItemResponse from(ChannelComparisonSnapshot snapshot) {
    return new ChannelComparisonItemResponse(
        snapshot.channelId(),
        snapshot.channelName(),
        snapshot.previewImageUrl(),
        snapshot.audienceSummary(),
        emptyIfNull(snapshot.adFormats()),
        emptyIfNull(snapshot.targetingMethods()),
        snapshot.minBudgetWon(),
        emptyIfNull(snapshot.advantages()),
        emptyIfNull(snapshot.tags()),
        snapshot.cpcWon(),
        snapshot.cpmWon(),
        snapshot.matchRate(),
        CountRangeResponse.from(snapshot.impressions()),
        CountRangeResponse.from(snapshot.clicks()));
  }

  /** 저장된 비교 항목(스냅샷)을 응답으로 변환한다. */
  public static ChannelComparisonItemResponse from(ChannelComparisonItem item) {
    return new ChannelComparisonItemResponse(
        item.getChannelId(),
        item.getChannelName(),
        item.getPreviewImageUrlSnap(),
        item.getAudienceSummarySnap(),
        emptyIfNull(item.getAdFormatsSnap()),
        emptyIfNull(item.getTargetingMethodsSnap()),
        item.getMinBudgetWonSnap(),
        emptyIfNull(item.getAdvantagesSnap()),
        emptyIfNull(item.getTagsSnap()),
        item.getCpcWon(),
        item.getCpmWon(),
        item.getMatchRate(),
        CountRangeResponse.of(item.getEstImpressionsMin(), item.getEstImpressionsMax()),
        CountRangeResponse.of(item.getEstClicksMin(), item.getEstClicksMax()));
  }

  /** 비로그인 비교용. 단가·장점·최소광고비·태그만 남긴다. */
  public ChannelComparisonItemResponse hideCatalogDetails() {
    return new ChannelComparisonItemResponse(
        channelId,
        channelName,
        previewImageUrl,
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
