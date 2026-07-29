package chaeso.zip.server.simulation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultCtrProviderTest {

  @Mock
  private ChannelProductRepository channelProductRepository;

  @InjectMocks
  private DefaultCtrProvider defaultCtrProvider;

  @Test
  @DisplayName("카탈로그 평균 CTR 을 한 번만 집계하고 이후에는 캐시를 쓴다")
  void cachesAverageAfterFirstLookup() {
    given(channelProductRepository.findAverageCtrPercent()).willReturn(1.75);

    assertThat(defaultCtrProvider.averageCtrPercent()).isEqualByComparingTo("1.75");
    assertThat(defaultCtrProvider.averageCtrPercent()).isEqualByComparingTo("1.75");

    verify(channelProductRepository, times(1)).findAverageCtrPercent();
  }

  @Test
  @DisplayName("평균을 구할 수 없으면 기본 2% 를 쓰고, 캐시하지 않아 다음 호출에서 다시 집계한다")
  void fallsBackWithoutCachingWhenAverageMissing() {
    given(channelProductRepository.findAverageCtrPercent()).willReturn(null);

    assertThat(defaultCtrProvider.averageCtrPercent())
        .isEqualByComparingTo(DefaultCtrProvider.LAST_RESORT_CTR_PERCENT);
    assertThat(defaultCtrProvider.averageCtrPercent())
        .isEqualByComparingTo(DefaultCtrProvider.LAST_RESORT_CTR_PERCENT);

    verify(channelProductRepository, times(2)).findAverageCtrPercent();
  }
}
