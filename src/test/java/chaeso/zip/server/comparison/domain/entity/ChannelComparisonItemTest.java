package chaeso.zip.server.comparison.domain.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChannelComparisonItemTest {

  @Test
  @DisplayName("comparisonId가 없으면 비교 항목을 생성할 수 없다")
  void rejectsNullComparisonId() {
    ChannelComparisonItem.ChannelComparisonItemBuilder builder = ChannelComparisonItem.builder()
        .comparisonId(null)
        .channelId(UUID.randomUUID());

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ChannelComparisonItem requires a comparisonId.");
  }

  @Test
  @DisplayName("channelId가 없으면 비교 항목을 생성할 수 없다")
  void rejectsNullChannelId() {
    ChannelComparisonItem.ChannelComparisonItemBuilder builder = ChannelComparisonItem.builder()
        .comparisonId(UUID.randomUUID())
        .channelId(null);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ChannelComparisonItem requires a channelId.");
  }
}
