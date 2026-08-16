package chaeso.zip.server.comparison.domain;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.vo.ExecutionType;
import chaeso.zip.server.support.ChannelCatalogFixture;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChannelComparisonSnapshotTest {

  @Test
  @DisplayName("채널에 집행 방식이 있으면 스냅샷에도 그대로 담긴다")
  void capturesExecutionTypeWhenPresent() {
    Channel channel = ChannelCatalogFixture.channel(UUID.randomUUID(), "11번가 광고");
    ReflectionTestUtils.setField(channel, "executionType", ExecutionType.SELF);

    ChannelComparisonSnapshot snapshot = ChannelComparisonSnapshot.catalogOnly(
        channel, List.of(), null, null, List.of());

    assertThat(snapshot.executionType()).isEqualTo("SELF");
  }

  @Test
  @DisplayName("채널에 집행 방식이 없으면 스냅샷 값도 null이다")
  void executionTypeIsNullWhenAbsent() {
    Channel channel = ChannelCatalogFixture.channel(UUID.randomUUID(), "11번가 광고");

    ChannelComparisonSnapshot snapshot = ChannelComparisonSnapshot.catalogOnly(
        channel, List.of(), null, null, List.of());

    assertThat(snapshot.executionType()).isNull();
  }
}
