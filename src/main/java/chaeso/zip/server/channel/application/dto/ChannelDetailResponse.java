package chaeso.zip.server.channel.application.dto;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.ExecutionType;
import chaeso.zip.server.channel.domain.vo.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "채널 상세")
public record ChannelDetailResponse(
    @Schema(description = "채널 식별자", example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,
    @Schema(description = "채널명", example = "11번가 광고", requiredMode = Schema.RequiredMode.REQUIRED)
    String name,
    @Schema(description = "채널명 아래 한 줄 설명",
        example = "월 방문자 수 상위 오픈마켓",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String tagline,
    @Schema(description = "로고 이미지 URL", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String logoUrl,
    @Schema(description = "채널 핵심 요약", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String description,
    @Schema(description = "대표 업종 코드값", example = "SHOPPING_COMMERCE",
        requiredMode = Schema.RequiredMode.REQUIRED)
    Category primaryCategory,
    @Schema(description = "매체 유형", example = "DISPLAY", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String mediaType,
    @Schema(description = "적합 업종 코드값 목록(없으면 빈 배열)",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<Category> suitableCategories,
    @Schema(description = "연령대 코드값 목록(없으면 빈 배열)",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<AgeBand> ageBandCodes,
    @Schema(description = "대표 연령대", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String primaryAgeBand,
    @Schema(description = "대표 성별 코드값", example = "FEMALE", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Gender primaryGender,
    @Schema(description = "오디언스 요약", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String audienceSummary,
    @Schema(description = "오디언스 특성", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String audienceTraits,
    @Schema(description = "채널 강점 목록(없으면 빈 배열)",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> advantages,
    @Schema(description = "최소 예산(원)", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Integer minBudgetWon,
    @Schema(description = "최대 예산(원)", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Integer maxBudgetWon,
    @Schema(description = "집행 방식 코드값", example = "SELF", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    ExecutionType executionType,
    @Schema(description = "지원 광고 형식 목록(없으면 빈 배열)",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> adFormats,
    @Schema(description = "지원 타게팅 방식 목록(없으면 빈 배열)",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> targetingMethods,
    @Schema(description = "채널 광고 상품 목록(상품 없는 채널은 빈 배열)",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<ProductResponse> products,
    @Schema(description = "오디언스 규모 지표 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    List<AudienceMetricResponse> audienceMetrics,
    @Schema(description = "집행 사례 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> references,
    @Schema(description = "추천 근거",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    RecommendationBasisResponse recommendationBasis) {

  public static ChannelDetailResponse from(
      Channel channel,
      List<ProductResponse> products,
      List<AudienceMetricResponse> audienceMetrics,
      List<String> references,
      RecommendationBasisResponse recommendationBasis) {
    return new ChannelDetailResponse(
        channel.getId(),
        channel.getName(),
        channel.getTagline(),
        channel.getLogoUrl(),
        channel.getDescription(),
        channel.getPrimaryCategory(),
        channel.getMediaType(),
        emptyIfNull(channel.getSuitableCategories()),
        emptyIfNull(channel.getAgeBandCodes()),
        channel.getPrimaryAgeBand(),
        channel.getPrimaryGender(),
        channel.getAudienceSummary(),
        channel.getAudienceTraits(),
        emptyIfNull(channel.getAdvantages()),
        channel.getMinBudgetWon(),
        channel.getMaxBudgetWon(),
        channel.getExecutionType(),
        emptyIfNull(channel.getAdFormats()),
        emptyIfNull(channel.getTargetingMethods()),
        products,
        audienceMetrics,
        references,
        recommendationBasis);
  }

  /** 목록은 값이 없어도 null 대신 빈 배열로 준다. */
  private static <T> List<T> emptyIfNull(List<T> values) {
    return values == null ? List.of() : values;
  }
}
