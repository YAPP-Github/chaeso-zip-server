package chaeso.zip.server.simulation.application.dto;

import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import java.util.List;

public record SimulationCommand(String serviceName, int totalBudgetWon, CampaignPeriod period,
                                List<AllocationCommand> allocations) {
}
