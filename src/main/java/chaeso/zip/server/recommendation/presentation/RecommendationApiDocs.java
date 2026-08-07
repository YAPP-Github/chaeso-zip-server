package chaeso.zip.server.recommendation.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
import chaeso.zip.server.recommendation.application.dto.SavedRecommendationResponse;
import chaeso.zip.server.recommendation.presentation.dto.SaveRecommendationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Recommendation", description = "채널 추천 API")
public interface RecommendationApiDocs {

  String RECOMMENDATION_EXAMPLE = """
      {
        "success": true,
        "data": [
          {
            "channelId": "550e8400-e29b-41d4-a716-446655440000",
            "channelName": "11번가 광고",
            "matchRate": 100,
            "recommendationReason": "쇼핑·커머스 업종, 설정한 광고 목적, 타깃 연령대에 적합하고 예산 내 집행이 가능해요",
            "primaryTarget": "20~40대 여성",
            "cpcWon": 120,
            "pricingModel": "CPM",
            "minBudgetWon": 3000,
            "estImpressions": { "min": 2833333, "max": 3833333 },
            "estClicks": { "min": 70833, "max": 95833 },
            "isExecutable": true
          },
          {
            "channelId": "9c1e8c2a-3f4d-4a5b-9c6d-7e8f9a0b1c2e",
            "channelName": "당근마켓 광고",
            "matchRate": 78,
            "recommendationReason": "쇼핑·커머스 업종, 설정한 광고 목적에 적합하지만 집행에는 5,000,000원이 더 필요해요",
            "primaryTarget": "전 연령 전 성별",
            "cpcWon": 200,
            "pricingModel": "SLOT",
            "minBudgetWon": 15000000,
            "estImpressions": { "min": 425000, "max": 575000 },
            "estClicks": { "min": 8500, "max": 11500 },
            "isExecutable": false,
            "shortfallWon": 5000000
          }
        ]
      }
      """;

  String RECOMMENDATION_SAVED_EXAMPLE = """
      {
        "success": true,
        "data": {
          "onboardingId": "550e8400-e29b-41d4-a716-446655440000",
          "channelCount": 1,
          "items": [
            {
              "channelId": "9c1e8c2a-3f4d-4a5b-9c6d-7e8f9a0b1c2e",
              "channelName": "11번가 광고",
              "matchRate": 100,
              "recommendationReason": "쇼핑·커머스 업종, 설정한 광고 목적, 타깃 연령대에 적합하고 예산 내 집행이 가능해요",
              "primaryTarget": "20~40대 여성",
              "cpcWon": 120,
              "pricingModel": "CPM",
              "minBudgetWon": 3000,
              "estImpressions": { "min": 2833333, "max": 3833333 },
              "estClicks": { "min": 70833, "max": 95833 },
              "isExecutable": true
            }
          ]
        }
      }
      """;

  String ONBOARDING_NOT_FOUND_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "ONB-007",
          "message": "온보딩 정보가 없습니다. id=550e8400-e29b-41d4-a716-446655440000",
          "fieldErrors": []
        }
      }
      """;

  String VALIDATION_ERROR_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "C-001",
          "message": "입력값이 올바르지 않습니다.",
          "fieldErrors": [
            {
              "field": "onboardingId",
              "value": "",
              "reason": "온보딩 응답 식별자는 필수입니다"
            }
          ]
        }
      }
      """;

  String CONCURRENT_SUBMISSION_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "ONB-006",
          "message": "동시에 제출된 요청이 있어 처리할 수 없습니다. 다시 시도해주세요.",
          "fieldErrors": []
        }
      }
      """;

  @Operation(operationId = "getRecommendations", summary = "온보딩 기반 채널 추천",
      description = """
          온보딩 응답을 업종·광고 목적·타깃 연령대로 매칭해 적합한 채널을 적합도 순으로 \
          반환한다.

          적합도가 같으면 집행 가능한 채널을 먼저, 그다음 매체명 순으로 정렬한다.

          매체별로 단가가 가장 싼 상품을 대표로 삼고, 온보딩 예산의 상한(budgetMax)과 \
          집행 기간을 적용한다. 예산이 최소 단가에 못 미치는 채널도 적합도가 있으면 \
          추천에 넣되, 노출·클릭은 최소 단가로 집행했을 때를 기준으로 채우고 \
          부족액(shortfallWon)을 함께 준다.""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
      description = "추천 성공. 맞는 채널이 없으면 빈 배열",
      useReturnTypeSchema = true,
      content = @Content(
          examples = @ExampleObject(name = "RECOMMENDATION", value = RECOMMENDATION_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 온보딩(ONB-007)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "ONBOARDING_NOT_FOUND",
              value = ONBOARDING_NOT_FOUND_EXAMPLE)))
  ApiResponse<List<RecommendationItemResponse>> getRecommendations(
      @Parameter(description = "온보딩 응답 식별자", required = true,
          example = "550e8400-e29b-41d4-a716-446655440000")
      UUID onboardingId);

  @SecurityRequirement(name = "bearerAuth")
  @Operation(operationId = "saveRecommendation", summary = "채널 추천 결과 저장",
      description = """
          추천을 다시 계산해 그 시점 값을 스냅샷으로 저장한다. 채널 1개가 1행이고, 요청한 \
          온보딩이 저장된 추천 1건을 가리키는 키가 된다.

          이후 채널의 단가·상품이 바뀌어도 저장된 추천은 변하지 않는다. 마이페이지는 이 저장분을 \
          그대로 읽는다.

          같은 온보딩으로 다시 저장하면 이전 추천을 지우고 다시 넣는다. 온보딩 응답은 불변이고 \
          추천도 결정적이라 결과는 같으며, 재요청·재시도로 행이 쌓이지 않는다.

          본인이 제출한 온보딩만 저장할 수 있다. 맞는 채널이 없으면 저장할 것도 없으므로 \
          channelCount 0 과 빈 배열을 반환한다.""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "저장 성공",
      useReturnTypeSchema = true,
      content = @Content(examples = @ExampleObject(name = "RECOMMENDATION_SAVED",
          value = RECOMMENDATION_SAVED_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
      description = "입력값 검증 실패(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않거나 본인이 제출하지 않은 온보딩(ONB-007)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "ONBOARDING_NOT_FOUND",
              value = ONBOARDING_NOT_FOUND_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
      description = "같은 온보딩으로 동시에 저장 요청이 겹침(ONB-006). 다시 시도하면 된다",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "CONCURRENT_SUBMISSION",
              value = CONCURRENT_SUBMISSION_EXAMPLE)))
  ApiResponse<SavedRecommendationResponse> saveRecommendation(
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody SaveRecommendationRequest request);
}
