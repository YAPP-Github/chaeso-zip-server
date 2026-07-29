package chaeso.zip.server.simulation.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.simulation.application.SimulationService;
import chaeso.zip.server.simulation.application.dto.SimulationResponse;
import chaeso.zip.server.simulation.presentation.dto.SimulationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
      @Valid @RequestBody SimulationRequest request) {
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
}
