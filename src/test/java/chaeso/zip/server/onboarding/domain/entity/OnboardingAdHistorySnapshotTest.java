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
    UUID onboardingId = UUID.randomUUID();
    var snapshotBuilder = OnboardingAdHistorySnapshot.snapshotBuilder()
        .onboardingId(onboardingId)
        .channelNameSnap(channelNameSnap)
        .budgetWonSnap(1000L);

    assertThatThrownBy(snapshotBuilder::build)
        .isInstanceOf(IllegalArgumentException.class);
  }
}
