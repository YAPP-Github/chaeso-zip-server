package chaeso.zip.server.sample.presentation;

import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.sample.application.SampleService;
import chaeso.zip.server.sample.application.dto.SampleResponse;
import chaeso.zip.server.sample.presentation.dto.CreateSampleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 샘플 REST API. 컨트롤러는 요청/응답 변환만 담당
 * 모든 응답은 {@link ApiResponse} 로 감싼다.
 */
@Tag(name = "Sample", description = "샘플 API (컨벤션 예시)")
@RestController
@RequestMapping("/api/v1/samples")
@RequiredArgsConstructor
public class SampleController {

  private final SampleService sampleService;

  @Operation(operationId = "createSample", summary = "샘플 생성", description = "이름을 받아 새로운 샘플을 생성한다.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패",
          content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  })
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SampleResponse> create(@Valid @RequestBody CreateSampleRequest request) {
    return ApiResponse.success(sampleService.create(request.toCommand()));
  }

  @Operation(operationId = "getSampleById", summary = "샘플 단건 조회", description = "식별자로 샘플을 조회한다.")
  @ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "샘플 없음",
          content = @Content(schema = @Schema(implementation = ApiResponse.class)))
  })
  @GetMapping("/{id}")
  public ApiResponse<SampleResponse> getById(
      @Parameter(description = "샘플 식별자", example = "1") @PathVariable Long id) {
    return ApiResponse.success(sampleService.getById(id));
  }

  @Operation(operationId = "getAllSamples", summary = "샘플 목록 조회", description = "전체 샘플 목록을 조회한다.")
  @GetMapping
  public ApiResponse<List<SampleResponse>> getAll() {
    return ApiResponse.success(sampleService.getAll());
  }
}
