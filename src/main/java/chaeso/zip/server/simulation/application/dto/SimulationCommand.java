package chaeso.zip.server.simulation.application.dto;

import chaeso.zip.server.simulation.domain.vo.SimPeriod;
import java.util.List;

public record SimulationCommand(int totalBudgetWon, SimPeriod period,
                                List<AllocationCommand> allocations) {
}
