package chaeso.zip.server.onboarding.domain.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OnboardingAdHistorySnapshotTest {

  @ParameterizedTest
  @NullSource
  @EmptySource
  @ValueSource(strings = " ")
  @DisplayName("channelNameSnap이 비어있으면 예외를 던진다")
  void rejectsBlankChannelName(String channelNameSnap) {
    UUID userId = UUID.randomUUID();
    assertThatThrownBy(() -> OnboardingAdHistorySnapshot.snapshot(
        userId, null, channelNameSnap, 1000L, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
