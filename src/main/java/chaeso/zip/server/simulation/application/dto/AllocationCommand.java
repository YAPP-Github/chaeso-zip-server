package chaeso.zip.server.simulation.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 매체 한 곳에 대한 예산 배분.
 *
 * @param channelId     배분 대상 채널
 * @param budgetWon     배분 예산(원). {@code 0} 은 미집행
 * @param allocationPct 전체 예산 대비 배분 비율(%)
 */
public record AllocationCommand(UUID channelId, int budgetWon, BigDecimal allocationPct) {
}
