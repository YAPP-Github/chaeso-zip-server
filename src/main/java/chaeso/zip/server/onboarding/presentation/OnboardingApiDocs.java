package chaeso.zip.server.onboarding.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.onboarding.application.dto.OnboardingSubmitResponse;
import chaeso.zip.server.onboarding.application.dto.PresignedFileUploadResult;
import chaeso.zip.server.onboarding.presentation.dto.PresignPerformanceFilesRequest;
import chaeso.zip.server.onboarding.presentation.dto.SubmitOnboardingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 온보딩 API 문서화 전용 인터페이스. 구현은 {@link OnboardingController}.
 */
@Tag(name = "Onboarding", description = "온보딩 정보 수집 API")
public interface OnboardingApiDocs {

  String SUBMIT_SUCCESS_EXAMPLE = """
      {
        "success": true,
        "data": {
          "onboardingId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
          "createdAt": "2026-07-28T10:00:00"
        },
        "error": null,
        "code": null
      }
      """;

  String PRESIGN_SUCCESS_EXAMPLE = """
      {
        "success": true,
        "data": [
          {
            "key": "ad-history/3f9c1e2a-....xlsx",
            "uploadUrl": "https://chaeso-zip-ad-history.s3.amazonaws.com/ad-history/3f9c1e2a-....xlsx?X-Amz-...",
            "contentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "expiresAt": "2026-07-28T10:05:00Z"
          }
        ],
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
              "field": "targetAgeBands",
              "value": "",
              "reason": "연령대를 1개 이상 선택해 주세요"
            }
          ]
        },
        "code": null
      }
      """;

  String INVALID_BUDGET_RANGE_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "ONB-001",
          "message": "최소 예산은 최대 예산보다 클 수 없습니다.",
          "fieldErrors": []
        },
        "code": null
      }
      """;

  String OBJECTIVE_NOT_ALLOWED_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "ONB-002",
          "message": "선택한 서비스 형태에서 사용할 수 없는 광고 목표입니다.",
          "fieldErrors": []
        },
        "code": null
      }
      """;

  String AD_EXPERIENCE_MISMATCH_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "ONB-003",
          "message": "집행 경험 여부와 입력한 집행 내역이 일치하지 않습니다.",
          "fieldErrors": []
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
          "message": "존재하지 않는 채널입니다. id=3fa85f64-5717-4562-b3fc-2c963f66afa6",
          "fieldErrors": []
        },
        "code": null
      }
      """;

  String CONCURRENT_SUBMISSION_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "ONB-006",
          "message": "동시에 제출된 요청이 있어 처리할 수 없습니다. 다시 시도해주세요.",
          "fieldErrors": []
        },
        "code": null
      }
      """;

  String PERFORMANCE_FILE_INVALID_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "ONB-008",
          "message": "첨부한 성과파일을 확인할 수 없습니다.",
          "fieldErrors": []
        },
        "code": null
      }
      """;

  String TOO_FEW_MANUAL_FIELDS_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "ONB-010",
          "message": "직접 입력한 집행 내역은 예산/집행기간/노출수/클릭수/전환수 중 2개 이상을 입력해야 합니다.",
          "fieldErrors": []
        },
        "code": null
      }
      """;

  String INVALID_AGE_BAND_SELECTION_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "ONB-011",
          "message": "잘 모르겠어요는 다른 연령대와 함께 선택할 수 없습니다.",
          "fieldErrors": []
        },
        "code": null
      }
      """;

  @Operation(operationId = "submitOnboarding", summary = "온보딩 제출",
      description = """
          로그인 여부와 관계없이 제출할 수 있다. \
          
          로그인 상태에서 다시 제출하면 이전 제출은 스냅샷으로 저장후 새로운 온보딩을 진행하고,\
          비로그인은 userId = null로 각각 저장된다.""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "제출 성공",
      useReturnTypeSchema = true,
      content = @Content(
          examples = @ExampleObject(name = "SUBMIT_SUCCESS", value = SUBMIT_SUCCESS_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
      description = "형식 오류(C-001) 또는 비즈니스 규칙 위반(ONB-001~003, ONB-008, ONB-010~011)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = {
              @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE),
              @ExampleObject(name = "INVALID_BUDGET_RANGE", value = INVALID_BUDGET_RANGE_EXAMPLE),
              @ExampleObject(name = "OBJECTIVE_NOT_ALLOWED", value = OBJECTIVE_NOT_ALLOWED_EXAMPLE),
              @ExampleObject(name = "AD_EXPERIENCE_MISMATCH", value = AD_EXPERIENCE_MISMATCH_EXAMPLE),
              @ExampleObject(name = "PERFORMANCE_FILE_INVALID", value = PERFORMANCE_FILE_INVALID_EXAMPLE),
              @ExampleObject(name = "TOO_FEW_MANUAL_FIELDS", value = TOO_FEW_MANUAL_FIELDS_EXAMPLE),
              @ExampleObject(name = "INVALID_AGE_BAND_SELECTION", value = INVALID_AGE_BAND_SELECTION_EXAMPLE)
          }))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 채널(CH-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "CHANNEL_NOT_FOUND", value = CHANNEL_NOT_FOUND_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
      description = "동시 제출 충돌(ONB-006)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "CONCURRENT_SUBMISSION", value = CONCURRENT_SUBMISSION_EXAMPLE)))
  ApiResponse<OnboardingSubmitResponse> submit(
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody SubmitOnboardingRequest request);

  @Operation(operationId = "presignOnboardingPerformanceFiles", summary = "성과파일 presigned URL 발급",
      description = """
          성과파일(xlsx/csv) 업로드용 presigned PUT URL을 로그인 여부와 관계없이 발급한다. \
          PUT 요청 시 응답의 contentType 값과 x-amz-tagging: retain=pending 헤더를 그대로 보내야 한다.""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공",
      useReturnTypeSchema = true,
      content = @Content(
          examples = @ExampleObject(name = "PRESIGN_SUCCESS", value = PRESIGN_SUCCESS_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
      description = "파일 개수(최대 5) 또는 확장자(xlsx/csv)나 크기(10MB) 위반(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  ApiResponse<List<PresignedFileUploadResult>> presignPerformanceFiles(
      @Valid @RequestBody PresignPerformanceFilesRequest request);
}
