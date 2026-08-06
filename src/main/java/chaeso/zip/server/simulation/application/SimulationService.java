package chaeso.zip.server.simulation.application;

import chaeso.zip.server.simulation.application.dto.SimulationCommand;
import chaeso.zip.server.simulation.application.dto.SimulationResponse;
import chaeso.zip.server.simulation.application.dto.SimulationSummaryResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SimulationService {

  /**
   * 예산 배분에 대한 추정치를 계산한다.
   */
  SimulationResponse estimate(SimulationCommand command);

  /**
   * 추정치를 계산한 뒤 결과를 스냅샷으로 저장한다.
   */
  SimulationResponse save(UUID userId, SimulationCommand command);

  /**
   * 사용자가 가장 최근에 저장한 결과를 재계산 없이 그대로 반환한다.
   *
   * @return 저장된 결과. 없으면 {@link Optional#empty()}
   */
  Optional<SimulationResponse> findLatest(UUID userId);

  /**
   * 사용자가 저장한 시뮬레이션 목록을 최신순으로 반환한다. 매체별 항목은 담지 않는다.
   */
  Page<SimulationSummaryResponse> findMySimulations(UUID userId, Pageable pageable);

  /**
   * 저장된 시뮬레이션 하나를 매체별 항목까지 재계산 없이 그대로 반환한다.
   */
  SimulationResponse findSimulation(UUID userId, UUID simulationId);
}
