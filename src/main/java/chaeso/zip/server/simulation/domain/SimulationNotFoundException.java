package chaeso.zip.server.simulation.domain;

import chaeso.zip.server.common.exception.BusinessException;
import java.util.UUID;

public class SimulationNotFoundException extends BusinessException {

  public SimulationNotFoundException(UUID simulationId) {
    super(SimulationErrorCode.SIMULATION_NOT_FOUND,
        "존재하지 않는 시뮬레이션입니다. id=" + simulationId);
  }
}
