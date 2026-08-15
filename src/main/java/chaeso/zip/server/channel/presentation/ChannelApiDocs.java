package chaeso.zip.server.channel.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.channel.presentation.dto.ChannelSearchRequest;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;

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
        "error": null,
        "code": null
      }
      """;

  String CHANNEL_DETAIL_EXAMPLE = """
      {
        "success": true,
        "data": {
          "id": "550e8400-e29b-41d4-a716-446655440000",
          "name": "11번가 광고",
          "tagline": "월 방문자 수 상위 오픈마켓",
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
              "expectedImpressions": 1500000,
              "expectedClicks": 5250,
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
              ],
              "isExecutable": true
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
          "references": ["A커머스 브랜드 신제품 런칭 캠페인", "B패션 시즌오프 프로모션"],
          "recommendationBasis": {
            "objective": "TRAFFIC",
            "category": "SHOPPING_COMMERCE",
            "budgetMin": 3000000,
            "budgetMax": 10000000
          },
          "tags": ["커머스 특화", "구매의도 타겟"]
        },
        "error": null,
        "code": null
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
        },
        "code": null
      }
      """;

  @Operation(operationId = "getChannels", summary = "채널 목록 조회",
      description = """
          채널을 조회한다. \
          page/size 를 모두 생략하면 페이지네이션 없이 전체 채널을 반환한다. \
          page 또는 size 중 하나라도 지정하면 페이지 조회로 동작한다(생략된 값은 page=0, size=12). size 는 최대 100. \
          name 지정 시 채널명으로 필터링. \
          primaryCategory 지정 시 그 대표 업종의 채널만 반환한다. 여러 번 넘기거나(primaryCategory=A&primaryCategory=B) \
          쉼표로 이어(primaryCategory=A,B) 여러 업종을 고를 수 있고, 그중 하나에 해당하면 남는다. \
          정렬은 name, createdAt 만 지원한다(형식: sort=name,desc / 기본값 name,asc)""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
      useReturnTypeSchema = true,
      content = @Content(
          examples = @ExampleObject(name = "CHANNEL_LIST", value = CHANNEL_LIST_EXAMPLE)))
  ApiResponse<PageResponse<ChannelListItemResponse>> getChannels(
      @ParameterObject ChannelSearchRequest request,
      @ParameterObject Sort sort);

  @SecurityRequirement(name = "bearerAuth")
  @Operation(operationId = "getChannel", summary = "채널 상세 조회",
      description = """
          채널 단건을 상세 조회한다. \
          채널 정보와 함께 광고 상품 목록, 오디언스 규모 지표, 집행 사례를 반환한다. \
          상품이 없는 채널은 products 를 빈 배열로 반환한다.

          추천 목록에서 들어온 경우 그 추천의 onboardingId 를 함께 넘기면, 추천 근거가 된 \
          온보딩 선택지(광고 목표·업종·예산)를 recommendationBasis 로 반환한다.

          매체 키워드(tags)는 채널 고유의 키워드라 맞춤 여부와 무관하게 누구에게나 같은 값을 \
          최대 2개까지 준다.""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
      useReturnTypeSchema = true,
      content = @Content(
          examples = @ExampleObject(name = "CHANNEL_DETAIL", value = CHANNEL_DETAIL_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 채널(CH-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "CHANNEL_NOT_FOUND", value = CHANNEL_NOT_FOUND_EXAMPLE)))
  ApiResponse<ChannelDetailResponse> getChannel(
      @Parameter(description = "채널 식별자", example = "550e8400-e29b-41d4-a716-446655440000")
      UUID id,
      @Parameter(description = "추천 목록에서 진입한 경우 그 추천의 온보딩 식별자. 생략하면 추천 근거 없이 상세만 반환한다",
          example = "8f14e45f-ceea-467a-9575-6f1a1f9b0d21")
      UUID onboardingId,
      @Parameter(hidden = true) UserPrincipal principal);
}
