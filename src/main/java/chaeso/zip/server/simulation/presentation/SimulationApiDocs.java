package chaeso.zip.server.simulation.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.simulation.application.dto.SimulationResponse;
import chaeso.zip.server.simulation.presentation.dto.SimulationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Simulation", description = "예산 시뮬레이터 API")
public interface SimulationApiDocs {

  String SIMULATION_EXAMPLE = """
      {
        "success": true,
        "data": {
          "totalBudgetWon": 3000000,
          "period": "M1",
          "totalEstImpressions": 1150000,
          "totalEstClicks": 23000,
          "executableChannelCount": 1,
          "items": [
            {
              "channelId": "550e8400-e29b-41d4-a716-446655440000",
              "channelName": "11번가 광고",
              "channelProductId": "7b1e8c2a-3f4d-4a5b-9c6d-7e8f9a0b1c2d",
              "allocatedBudgetWon": 2000000,
              "allocationPct": 66.7,
              "estImpressions": { "min": 566667, "max": 766667 },
              "estClicks": { "min": 11333, "max": 15333 },
              "cpcWon": 150,
              "cpmWon": 3000,
              "isExecutable": true,
              "shortfallWon": null,
              "basisNote": "매체 소개서 기반 / VAT 별도 가정 / CTR 미제공 시 전체 평균 CTR 적용"
            },
            {
              "channelId": "9c1e8c2a-3f4d-4a5b-9c6d-7e8f9a0b1c2e",
              "channelName": "당근마켓 광고",
              "channelProductId": null,
              "allocatedBudgetWon": 1000000,
              "allocationPct": 33.3,
              "estImpressions": null,
              "estClicks": null,
              "cpcWon": null,
              "cpmWon": null,
              "isExecutable": false,
              "shortfallWon": null,
              "basisNote": "견적 문의 필요 (등록된 단가 정보 없음) / 매체 소개서 기반 / VAT 별도 가정 / CTR 미제공 시 전체 평균 CTR 적용"
            }
          ]
        }
      }
      """;

  String SIMULATION_SAVED_EXAMPLE = """
      {
        "success": true,
        "data": {
          "simulationId": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
          "totalBudgetWon": 3000000,
          "period": "M1",
          "totalEstImpressions": 1150000,
          "totalEstClicks": 23000,
          "executableChannelCount": 1,
          "items": [
            {
              "channelId": "550e8400-e29b-41d4-a716-446655440000",
              "channelName": "11번가 광고",
              "channelProductId": "7b1e8c2a-3f4d-4a5b-9c6d-7e8f9a0b1c2d",
              "allocatedBudgetWon": 3000000,
              "allocationPct": 100,
              "estImpressions": { "min": 850000, "max": 1150000 },
              "estClicks": { "min": 17000, "max": 23000 },
              "cpcWon": 150,
              "cpmWon": 3000,
              "isExecutable": true,
              "shortfallWon": null,
              "basisNote": "매체 소개서 기반 / VAT 별도 가정 / CTR 미제공 시 전체 평균 CTR 적용"
            }
          ]
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
              "field": "totalBudgetWon",
              "value": "50000",
              "reason": "100000 이상이어야 합니다"
            }
          ]
        }
      }
      """;

  String CHANNEL_NOT_FOUND_EXAMPLE = """
      {
        "success": false,
        "error": {
          "code": "CH-001",
          "message": "존재하지 않는 채널입니다. id=550e8400-e29b-41d4-a716-446655440000",
          "fieldErrors": []
        }
      }
      """;

  @Operation(operationId = "estimateSimulation", summary = "예산 시뮬레이션 계산",
      description = """
          매체별 예산 배분에 대한 예상 노출·클릭을 계산해 반환한다. 저장하지 않으므로 응답에 \
          simulationId 가 없다. 로그인 없이 호출할 수 있다. \
          매체별로 단가가 가장 싼 상품을 대표로 삼으며, CTR 이 없는 상품은 카탈로그 전체 평균 CTR 로 \
          클릭을 계산한다. 집행 가능 여부는 기간과 무관하게 배분 예산이 단가를 넘는지로만 판단한다. \
          cpcWon 은 모든 매체를 클릭당 비용 하나로 통일해 보여주는 값으로, 클릭당 과금 매체는 단가 \
          그대로이고 그 외 매체는 배분 예산 / 예상 클릭 수(중앙값)로 환산한다.""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "계산 성공",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "SIMULATION", value = SIMULATION_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
      description = "입력값 검증 실패(C-001). 총 예산 범위(10만~500만), 기간, 배분 목록을 확인한다",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 채널(CH-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "CHANNEL_NOT_FOUND", value = CHANNEL_NOT_FOUND_EXAMPLE)))
  ApiResponse<SimulationResponse> estimateSimulation(
      @Valid @RequestBody SimulationRequest request);

  @Operation(operationId = "saveSimulation", summary = "예산 시뮬레이션 결과 저장",
      description = """
          계산 결과를 스냅샷으로 저장하고 simulationId 를 포함해 반환한다. 사용자당 저장 개수에 \
          제한이 없으며, 저장된 결과는 수정되지 않는다.""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "저장 성공",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "SIMULATION_SAVED", value = SIMULATION_SAVED_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
      description = "입력값 검증 실패(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
      description = "인증 필요(C-004)")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 채널(CH-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "CHANNEL_NOT_FOUND", value = CHANNEL_NOT_FOUND_EXAMPLE)))
  ApiResponse<SimulationResponse> saveSimulation(
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody SimulationRequest request);

  @Operation(operationId = "getLatestSimulation", summary = "최신 시뮬레이션 결과 불러오기",
      description = """
          사용자가 가장 최근에 저장한 결과를 재계산 없이 그대로 반환한다. \
          저장된 결과가 없으면 본문 없이 204 를 반환한다.""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "SIMULATION_SAVED", value = SIMULATION_SAVED_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204",
      description = "저장된 결과 없음")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
      description = "인증 필요(C-004)")
  ResponseEntity<ApiResponse<SimulationResponse>> getLatestSimulation(
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);
}
