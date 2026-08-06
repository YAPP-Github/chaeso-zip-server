package chaeso.zip.server.simulation.domain;

import chaeso.zip.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SimulationErrorCode implements ErrorCode {

  SIMULATION_NOT_FOUND(HttpStatus.NOT_FOUND, "SIM-001", "존재하지 않는 시뮬레이션입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
