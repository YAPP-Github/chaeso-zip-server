package chaeso.zip.server.simulation.domain.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BudgetBasis {
  TOTAL("총 예산");

  private final String description;
}
