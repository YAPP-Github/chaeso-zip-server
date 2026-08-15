package chaeso.zip.server.simulation.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.PageResponse;
import chaeso.zip.server.simulation.application.SimulationService;
import chaeso.zip.server.simulation.application.dto.SimulationResponse;
import chaeso.zip.server.simulation.application.dto.SimulationSummaryResponse;
import chaeso.zip.server.simulation.presentation.dto.SaveSimulationRequest;
import chaeso.zip.server.simulation.presentation.dto.SimulationPageRequest;
import chaeso.zip.server.simulation.presentation.dto.SimulationRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulations")
@RequiredArgsConstructor
public class SimulationController implements SimulationApiDocs {

  private final SimulationService simulationService;

  @Override
  @PostMapping("/estimate")
  public ApiResponse<SimulationResponse> estimateSimulation(
      @Valid @RequestBody SimulationRequest request) {
    return ApiResponse.success(simulationService.estimate(request.toCommand()));
  }

  @Override
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SimulationResponse> saveSimulation(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody SaveSimulationRequest request) {
    return ApiResponse.success(
        simulationService.save(principal.userId(), request.toCommand()));
  }

  @Override
  @GetMapping("/latest")
  public ResponseEntity<ApiResponse<SimulationResponse>> getLatestSimulation(
      @AuthenticationPrincipal UserPrincipal principal) {
    return simulationService.findLatest(principal.userId())
        .map(simulation -> ResponseEntity.ok(ApiResponse.success(simulation)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Override
  @GetMapping
  public ApiResponse<PageResponse<SimulationSummaryResponse>> getMySimulations(
      @AuthenticationPrincipal UserPrincipal principal,
      @ParameterObject SimulationPageRequest request) {
    return ApiResponse.success(PageResponse.from(
        simulationService.findMySimulations(principal.userId(), request.toPageable())));
  }

  @Override
  @GetMapping("/{simulationId}")
  public ApiResponse<SimulationResponse> getSimulation(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID simulationId) {
    return ApiResponse.success(
        simulationService.findSimulation(principal.userId(), simulationId));
  }
}
