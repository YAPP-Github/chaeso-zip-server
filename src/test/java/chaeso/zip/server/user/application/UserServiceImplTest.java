package chaeso.zip.server.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import chaeso.zip.server.auth.domain.InvalidTokenException;
import chaeso.zip.server.support.UserFixture;
import chaeso.zip.server.user.application.dto.UpdateProfileCommand;
import chaeso.zip.server.user.application.dto.UserProfileResponse;
import chaeso.zip.server.user.application.dto.WithdrawalResponse;
import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserNotFoundException;
import chaeso.zip.server.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-08-20T03:00:00Z");

  @Mock
  private UserRepository userRepository;

  private UserServiceImpl userService;

  @BeforeEach
  void setUp() {
    userService = new UserServiceImpl(userRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Nested
  @DisplayName("내 정보 조회")
  class GetMyProfile {

    @Test
    @DisplayName("계정/이름/회사/직무를 반환한다")
    void returnsUserFields() {
      User user = UserFixture.user();
      given(userRepository.findByIdAndDeletedAtIsNull(user.getId())).willReturn(Optional.of(user));

      UserProfileResponse response = userService.getMyProfile(user.getId());

      assertThat(response).isEqualTo(UserProfileResponse.from(user));
    }

    @Test
    @DisplayName("회원이 없으면 UserNotFoundException을 던진다")
    void throwsWhenUserMissing() {
      UUID userId = UUID.randomUUID();
      given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> userService.getMyProfile(userId))
          .isInstanceOf(UserNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("내 정보 수정")
  class UpdateMyProfile {

    @Test
    @DisplayName("회사/직무를 변경한다")
    void changesOnlyCompanyAndOccupation() {
      User user = UserFixture.user();
      given(userRepository.findByIdAndDeletedAtIsNull(user.getId())).willReturn(Optional.of(user));

      UserProfileResponse response = userService.updateMyProfile(user.getId(),
          new UpdateProfileCommand("새회사", Occupation.DATA));

      assertThat(response.nickname()).isEqualTo(user.getNickname());
      assertThat(response.email()).isEqualTo(user.getEmail());
      assertThat(response.companyName()).isEqualTo("새회사");
      assertThat(response.occupation()).isEqualTo(Occupation.DATA);
    }
  }

  @Nested
  @DisplayName("회원 탈퇴")
  class Withdraw {

    @Test
    @DisplayName("잠근 회원을 탈퇴 처리하고 탈퇴 시각을 반환한다")
    void withdrawsLockedUser() {
      UUID userId = UUID.randomUUID();
      User user = UserFixture.user();
      given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user));

      WithdrawalResponse response = userService.withdraw(userId, 0);

      assertThat(response.withdrawnAt()).isEqualTo(NOW);
      assertThat(user.getSessionVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("무효화된 세션으로 탈퇴를 반복하면 거절한다")
    void repeatedWithdrawalWithStaleSessionIsRejected() {
      UUID userId = UUID.randomUUID();
      User user = UserFixture.user();
      given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user));

      userService.withdraw(userId, 0);

      assertThatThrownBy(() -> userService.withdraw(userId, 0))
          .isInstanceOf(InvalidTokenException.class);
      assertThat(user.getSessionVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("회원이 없으면 UserNotFoundException을 던진다")
    void missingUser() {
      UUID userId = UUID.randomUUID();
      given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> userService.withdraw(userId, 0))
          .isInstanceOf(UserNotFoundException.class);
    }
  }
}
