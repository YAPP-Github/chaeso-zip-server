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
}
