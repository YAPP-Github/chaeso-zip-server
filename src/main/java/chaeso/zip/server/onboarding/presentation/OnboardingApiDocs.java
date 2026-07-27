package chaeso.zip.server.onboarding.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.onboarding.application.dto.OnboardingSubmitResponse;
import chaeso.zip.server.onboarding.presentation.dto.SubmitOnboardingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 온보딩 API 문서화 전용 인터페이스. 구현은 {@link OnboardingController}.
 */
@Tag(name = "Onboarding", description = "온보딩 정보 수집 API")
public interface OnboardingApiDocs {

  String VALIDATION_ERROR_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "C-001",
          "message": "입력값이 올바르지 않습니다.",
          "fieldErrors": [
            {
              "field": "targetAgeBands",
              "value": "",
              "reason": "연령대를 1개 이상 선택해 주세요"
            }
          ]
        }
      }
      """;

  String INVALID_BUDGET_RANGE_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "ONB-001",
          "message": "최소 예산은 최대 예산보다 클 수 없습니다.",
          "fieldErrors": []
        }
      }
      """;

  String OBJECTIVE_NOT_ALLOWED_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "ONB-002",
          "message": "선택한 서비스 형태에서 사용할 수 없는 광고 목표입니다.",
          "fieldErrors": []
        }
      }
      """;

  String AD_EXPERIENCE_MISMATCH_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "ONB-003",
          "message": "집행 경험 여부와 입력한 집행 내역이 일치하지 않습니다.",
          "fieldErrors": []
        }
      }
      """;

  String TOO_MANY_AD_HISTORY_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "ONB-004",
          "message": "집행 내역은 최대 50건까지 입력할 수 있습니다.",
          "fieldErrors": []
        }
      }
      """;

  String CHANNEL_NOT_FOUND_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "CH-001",
          "message": "존재하지 않는 채널입니다.",
          "fieldErrors": []
        }
      }
      """;

  String INVALID_AD_PERIOD_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "ONB-005",
          "message": "집행 종료일은 시작일보다 빠를 수 없습니다.",
          "fieldErrors": []
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

  String UNAUTHORIZED_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "C-004",
          "message": "인증이 필요합니다.",
          "fieldErrors": []
        }
      }
      """;

  @SecurityRequirement(name = "bearerAuth")
  @Operation(operationId = "submitOnboarding", summary = "온보딩 제출",
      description = "온보딩 정보를 저장한다. 이미 제출한 적이 있으면 이전 응답은 비활성으로 내려가고 새 응답이 활성이 된다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "제출 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
      description = "입력값 검증 실패(C-001) 또는 값 사이의 관계 규칙 위반(ONB-001~005)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = {
              @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE),
              @ExampleObject(name = "INVALID_BUDGET_RANGE", value = INVALID_BUDGET_RANGE_EXAMPLE),
              @ExampleObject(name = "OBJECTIVE_NOT_ALLOWED", value = OBJECTIVE_NOT_ALLOWED_EXAMPLE),
              @ExampleObject(name = "AD_EXPERIENCE_MISMATCH", value = AD_EXPERIENCE_MISMATCH_EXAMPLE),
              @ExampleObject(name = "TOO_MANY_AD_HISTORY", value = TOO_MANY_AD_HISTORY_EXAMPLE),
              @ExampleObject(name = "INVALID_AD_PERIOD", value = INVALID_AD_PERIOD_EXAMPLE)
          }))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "미인증 요청(C-004)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "UNAUTHORIZED", value = UNAUTHORIZED_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 채널(CH-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "CHANNEL_NOT_FOUND", value = CHANNEL_NOT_FOUND_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
      description = "동시에 재제출되어 활성 온보딩이 중복 생성될 뻔함(ONB-006). 재시도 필요",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "CONCURRENT_SUBMISSION", value = CONCURRENT_SUBMISSION_EXAMPLE)))
  ApiResponse<OnboardingSubmitResponse> submit(
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody SubmitOnboardingRequest request);
}
