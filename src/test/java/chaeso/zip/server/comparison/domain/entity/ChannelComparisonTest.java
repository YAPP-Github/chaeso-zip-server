package chaeso.zip.server.comparison.domain.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChannelComparisonTest {

  @Test
  @DisplayName("userId가 없으면 채널 비교를 생성할 수 없다")
  void rejectsNullUserId() {
    ChannelComparison.ChannelComparisonBuilder builder = ChannelComparison.builder()
        .userId(null)
        .onboardingId(UUID.randomUUID());

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ChannelComparison requires a userId.");
  }
}
