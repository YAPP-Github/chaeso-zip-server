package chaeso.zip.server.estimation.application;

import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CTR 을 제공하지 않는 상품의 클릭 추정에 쓸 기본 CTR 을 공급한다.
 *
 * 기본값 = CTR 이 명시된 전체 상품의 평균
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultCtrProvider {

  /** 카탈로그에 CTR 이 전혀 없을 경우 기본 CTR(%). */
  static final BigDecimal LAST_RESORT_CTR_PERCENT = new BigDecimal("2");

  private final ChannelProductRepository channelProductRepository;

  private volatile BigDecimal cachedAverage;

  /**
   * 기본 CTR(%)
   */
  public BigDecimal averageCtrPercent() {
    BigDecimal cached = cachedAverage;
    if (cached != null) {
      return cached;
    }

    Double average = channelProductRepository.findAverageCtrPercent();
    if (average == null) {
      log.warn("CTR 이 명시된 상품이 없어 기본 CTR {}% 를 사용합니다.", LAST_RESORT_CTR_PERCENT);
      return LAST_RESORT_CTR_PERCENT;
    }

    BigDecimal resolved = BigDecimal.valueOf(average);
    cachedAverage = resolved;
    return resolved;
  }
}
