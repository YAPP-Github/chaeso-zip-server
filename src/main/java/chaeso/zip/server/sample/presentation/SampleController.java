package chaeso.zip.server.sample.presentation;

import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.sample.application.SampleService;
import chaeso.zip.server.sample.application.dto.SampleResponse;
import chaeso.zip.server.sample.presentation.dto.CreateSampleRequest;
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
@RestController
@RequestMapping("/api/v1/samples")
@RequiredArgsConstructor
public class SampleController {

  private final SampleService sampleService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SampleResponse> create(@Valid @RequestBody CreateSampleRequest request) {
    return ApiResponse.success(sampleService.create(request.name()));
  }

  @GetMapping("/{id}")
  public ApiResponse<SampleResponse> getById(@PathVariable Long id) {
    return ApiResponse.success(sampleService.getById(id));
  }

  @GetMapping
  public ApiResponse<List<SampleResponse>> getAll() {
    return ApiResponse.success(sampleService.getAll());
  }
}
