package chaeso.zip.server.channel.presentation;

import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;

@Tag(name = "Channel", description = "채널 카탈로그 API")
public interface ChannelApiDocs {

  String CHANNEL_LIST_EXAMPLE = """
      {
        "success": true,
        "data": {
          "content": [
            {
              "id": "550e8400-e29b-41d4-a716-446655440000",
              "name": "11번가 광고",
              "logoUrl": "https://cdn.chaeso.zip/channels/11st-logo.png",
              "description": "월 방문자 수 상위 오픈마켓 채널",
              "primaryCategory": "SHOPPING_COMMERCE"
            }
          ],
          "number": 0,
          "size": 12,
          "totalElements": 101,
          "totalPages": 9,
          "first": true,
          "last": false
        },
        "error": null
      }
      """;

  String CHANNEL_DETAIL_EXAMPLE = """
      {
        "success": true,
        "data": {
          "id": "550e8400-e29b-41d4-a716-446655440000",
          "name": "11번가 광고",
          "logoUrl": "https://cdn.chaeso.zip/channels/11st-logo.png",
          "description": "월 방문자 수 상위 오픈마켓 채널",
          "primaryCategory": "SHOPPING_COMMERCE",
          "mediaType": "DISPLAY",
          "suitableCategories": ["SHOPPING_COMMERCE", "FASHION_BEAUTY"],
          "ageBandCodes": ["AGE_20S", "AGE_30S"],
          "primaryAgeBand": "30대",
          "primaryGender": "FEMALE",
          "audienceSummary": "2040 여성 중심의 쇼핑 관심 오디언스",
          "audienceTraits": "가격 민감도가 높고 프로모션 반응률이 우수",
          "advantages": ["높은 구매 전환율", "리타게팅 지원"],
          "minBudgetWon": 3000000,
          "maxBudgetWon": 50000000,
          "executionType": "SELF",
          "adFormats": ["배너", "동영상"],
          "targetingMethods": ["관심사", "리타게팅"],
          "products": [
            {
              "id": "7b1e8c2a-3f4d-4a5b-9c6d-7e8f9a0b1c2d",
              "productName": "메인 상단 배너",
              "inventoryType": "DISPLAY",
              "supportedObjectives": ["AWARENESS", "TRAFFIC"],
              "minBudgetWon": 3000000,
              "maxBudgetWon": 20000000,
              "ctr": 0.35,
              "ctrMin": 0.20,
              "ctrMax": 0.55,
              "expectedImpressions": 1500000,
              "expectedPeriod": "2주",
              "pricing": [
                {
                  "pricingModel": "CPM",
                  "value": 8000,
                  "valueMax": null,
                  "unitPeriod": null,
                  "unitDays": null,
                  "segment": null,
                  "priceType": "LIST",
                  "vat": "EXCLUDED",
                  "currency": "KRW",
                  "validPeriod": "2025-01-01 ~ 2025-12-31"
                }
              ]
            }
          ],
          "audienceMetrics": [
            {
              "metricName": "MAU",
              "valueNumeric": 12000000,
              "valueText": null,
              "unit": "명",
              "period": "월"
            }
          ],
          "references": ["A커머스 브랜드 신제품 런칭 캠페인", "B패션 시즌오프 프로모션"]
        },
        "error": null
      }
      """;

  String CHANNEL_NOT_FOUND_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "CH-001",
          "message": "존재하지 않는 채널입니다. id=550e8400-e29b-41d4-a716-446655440000",
          "fieldErrors": []
        }
      }
      """;

  @Operation(operationId = "getChannels", summary = "채널 목록 조회",
      description = """
          채널을 페이지 단위로 조회한다. \
          미지정 시 이름순 12개. name 지정 시 채널명으로 필터링""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "CHANNEL_LIST", value = CHANNEL_LIST_EXAMPLE)))
  ApiResponse<PageResponse<ChannelListItemResponse>> getChannels(
      @Parameter(description = "채널명 검색어", example = "11번가") String name,
      @ParameterObject Pageable pageable);

  @Operation(operationId = "getChannel", summary = "채널 상세 조회",
      description = """
          채널 단건을 상세 조회한다. \
          채널 정보와 함께 광고 상품 목록, 오디언스 규모 지표, 집행 사례를 반환한다. \
          상품이 없는 채널은 products 를 빈 배열로 반환한다.""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "CHANNEL_DETAIL", value = CHANNEL_DETAIL_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 채널(CH-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "CHANNEL_NOT_FOUND", value = CHANNEL_NOT_FOUND_EXAMPLE)))
  ApiResponse<ChannelDetailResponse> getChannel(
      @Parameter(description = "채널 식별자", example = "550e8400-e29b-41d4-a716-446655440000")
      UUID id);
}
