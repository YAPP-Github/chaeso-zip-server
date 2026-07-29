package chaeso.zip.server.simulation.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SimPeriod {
  W1(7, "1주"),
  W2(14, "2주"),
  M1(30, "1개월"),
  M3(90, "3개월");

  private final int days;
  private final String description;
}
