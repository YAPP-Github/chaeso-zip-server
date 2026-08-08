package chaeso.zip.server.support;

import chaeso.zip.server.performance.domain.entity.AdPerformance;
import chaeso.zip.server.performance.domain.entity.AdPerformance.AdPerformanceBuilder;
import chaeso.zip.server.performance.domain.vo.PerfSource;
import java.util.UUID;

/** 테스트용 AdPerformance 생성 헬퍼.*/
public final class AdPerformanceFixture {

  private AdPerformanceFixture() {
  }

  /** MANUAL/예산 100만/노출 10만/클릭 2천/전환 10건, userId/channelId 랜덤. */
  public static AdPerformance adPerformance() {
    return builder().build();
  }

  /** 기본값이 채워진 빌더. 필요한 필드만 덮어써서 쓴다. */
  public static AdPerformanceBuilder builder() {
    return AdPerformance.builder()
        .userId(UUID.randomUUID())
        .sourceType(PerfSource.MANUAL)
        .channelId(UUID.randomUUID())
        .budgetWon(1_000_000L)
        .impressions(100_000L)
        .clicks(2_000L)
        .conversions(10L);
  }
}
