package chaeso.zip.server.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.auth.domain.AuthProvider;
import chaeso.zip.server.support.UserFixture;
import java.time.LocalDateTime;
import java.time.Month;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final LocalDateTime WITHDRAWN_AT = LocalDateTime.of(2026, Month.AUGUST, 20, 3, 0);

  @Test
  @DisplayName("첫 탈퇴는 시각을 기록하고 마케팅 수신을 해제하며 세션 버전을 증가시킨다")
  void withdraw() {
    User user = UserFixture.user(true);

    user.withdraw(WITHDRAWN_AT);

    assertThat(user.getDeletedAt()).isEqualTo(WITHDRAWN_AT);
    assertThat(user.isMarketingAgreed()).isFalse();
    assertThat(user.getMarketingAgreedAt()).isNull();
    assertThat(user.getSessionVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("중복 탈퇴는 최초 탈퇴 시각과 세션 버전을 바꾸지 않는다")
  void repeatedWithdrawIsIdempotent() {
    User user = UserFixture.user(false);
    user.withdraw(WITHDRAWN_AT);

    user.withdraw(WITHDRAWN_AT.plusDays(3));

    assertThat(user.getDeletedAt()).isEqualTo(WITHDRAWN_AT);
    assertThat(user.getSessionVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("로그인 기록에는 호출자가 전달한 UTC 시각을 사용한다")
  void recordLoginUsesProvidedTime() {
    User user = UserFixture.user(false);
    LocalDateTime loggedInAt = LocalDateTime.of(2026, Month.AUGUST, 9, 14, 30);

    user.recordLogin(AuthProvider.LOCAL, loggedInAt);

    assertThat(user.getLastLoginAt()).isEqualTo(loggedInAt);
    assertThat(user.getLastLoginProvider()).isEqualTo(AuthProvider.LOCAL);
  }

  @Test
  @DisplayName("탈퇴 유예기간(30일) 이내면 복구되어 탈퇴 시각이 지워진다")
  void restoreIfWithinGracePeriod_withinPeriod_restores() {
    User user = UserFixture.user(false);
    user.withdraw(WITHDRAWN_AT);

    boolean restored = user.restoreIfWithinGracePeriod(WITHDRAWN_AT.plusDays(29));

    assertThat(restored).isTrue();
    assertThat(user.getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("탈퇴 유예기간(30일) 경계 시각까지는 복구된다")
  void restoreIfWithinGracePeriod_atBoundary_restores() {
    User user = UserFixture.user(false);
    user.withdraw(WITHDRAWN_AT);

    boolean restored = user.restoreIfWithinGracePeriod(WITHDRAWN_AT.plusDays(30));

    assertThat(restored).isTrue();
    assertThat(user.getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("탈퇴 유예기간(30일)이 지나면 복구하지 않고 탈퇴 상태를 유지한다")
  void restoreIfWithinGracePeriod_pastPeriod_doesNotRestore() {
    User user = UserFixture.user(false);
    user.withdraw(WITHDRAWN_AT);

    boolean restored = user.restoreIfWithinGracePeriod(WITHDRAWN_AT.plusDays(31));

    assertThat(restored).isFalse();
    assertThat(user.getDeletedAt()).isEqualTo(WITHDRAWN_AT);
  }

  @Test
  @DisplayName("탈퇴하지 않은 회원은 복구 시도해도 아무 일도 일어나지 않는다")
  void restoreIfWithinGracePeriod_notWithdrawn_doesNothing() {
    User user = UserFixture.user(false);

    boolean restored = user.restoreIfWithinGracePeriod(WITHDRAWN_AT);

    assertThat(restored).isFalse();
    assertThat(user.getDeletedAt()).isNull();
  }

  @Test
  @DisplayName("탈퇴 유예기간 이내인지 확인해도 상태를 바꾸지 않는다")
  void isWithinWithdrawalGracePeriod_doesNotMutateState() {
    User user = UserFixture.user(false);
    user.withdraw(WITHDRAWN_AT);

    boolean within = user.isWithinWithdrawalGracePeriod(WITHDRAWN_AT.plusDays(1));

    assertThat(within).isTrue();
    assertThat(user.getDeletedAt()).isEqualTo(WITHDRAWN_AT);
  }
}
