package chaeso.zip.server.comparison.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonResponse;
import chaeso.zip.server.comparison.presentation.dto.ChannelComparisonRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "ChannelComparison", description = "채널 비교 API")
public interface ChannelComparisonApiDocs {

  String COMPARISON_STATIC_EXAMPLE = """
      {
        "success": true,
        "data": {
          "items": [
            {
              "channelId": "550e8400-e29b-41d4-a716-446655440000",
              "channelName": "11번가 광고",
              "audienceSummary": "20~40대 여성",
              "adFormats": ["배너"],
              "targetingMethods": ["관심사"],
              "minBudgetWon": 3000000,
              "advantages": ["빠른 노출", "다양한 타기팅"],
              "tags": ["커머스 특화", "구매 전환"],
              "cpcWon": null,
              "cpmWon": 3000,
              "matchRate": null,
              "estImpressions": null,
              "estClicks": null
            }
          ]
        },
        "error": null,
        "code": null
      }
      """;

  String COMPARISON_PERSONALIZED_EXAMPLE = """
      {
        "success": true,
        "data": {
          "items": [
            {
              "channelId": "550e8400-e29b-41d4-a716-446655440000",
              "channelName": "11번가 광고",
              "audienceSummary": "20~40대 여성",
              "adFormats": ["배너"],
              "targetingMethods": ["관심사"],
              "minBudgetWon": 3000000,
              "advantages": ["빠른 노출", "다양한 타기팅"],
              "tags": ["CATEGORY", "OBJECTIVE"],
              "cpcWon": 120,
              "cpmWon": 3000,
              "matchRate": 100,
              "estImpressions": { "min": 850000, "max": 1150000 },
              "estClicks": { "min": 21250, "max": 28750 }
            }
          ]
        },
        "error": null,
        "code": null
      }
      """;

  String VALIDATION_ERROR_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "C-001",
          "message": "입력값이 올바르지 않습니다.",
          "fieldErrors": [
            {
              "field": "channelIds",
              "value": "",
              "reason": "비교할 채널을 1개 이상 선택해 주세요"
            }
          ]
        },
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

  String ONBOARDING_NOT_FOUND_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "ONB-007",
          "message": "온보딩 정보가 없습니다. id=550e8400-e29b-41d4-a716-446655440000",
          "fieldErrors": []
        },
        "code": null
      }
      """;

  @Operation(operationId = "getChannelComparison", summary = "채널 비교 조회",
      description = """
          채널을 1~3개까지 비교한다. \
          onboardingId 가 있으면 온보딩 조건으로 적합도, 태그(최대 2개), 예상 노출·클릭 수를 계산한다. \
          onboardingId 가 없으면 기본 태그 전체를 반환하고 적합도와 예상 노출·클릭 수는 null이다. \
          예산이 부족하면 예상 노출·클릭 수는 null이며, 등록된 CPC·CPM 단가를 반환한다. \
          회원이 만든 온보딩은 해당 회원만 사용할 수 있다.""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
      description = "조회 성공",
      useReturnTypeSchema = true,
      content = @Content(
          examples = {
              @ExampleObject(name = "STATIC_COMPARISON", summary = "일반 비교(onboardingId 없음)", value = COMPARISON_STATIC_EXAMPLE),
              @ExampleObject(name = "PERSONALIZED_COMPARISON", summary = "온보딩 기반 비교(onboardingId 있음)", value = COMPARISON_PERSONALIZED_EXAMPLE)
          }))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
      description = "입력값 검증 실패(C-001). 채널을 1개 이상 3개 이하로 중복 없이 선택한다",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 채널 또는 온보딩(CH-001 또는 ONB-007)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = {
              @ExampleObject(name = "CHANNEL_NOT_FOUND", value = CHANNEL_NOT_FOUND_EXAMPLE),
              @ExampleObject(name = "ONBOARDING_NOT_FOUND", value = ONBOARDING_NOT_FOUND_EXAMPLE)
          }))
  ApiResponse<ChannelComparisonResponse> getChannelComparison(
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
      @Valid @ParameterObject ChannelComparisonRequest request);
}
