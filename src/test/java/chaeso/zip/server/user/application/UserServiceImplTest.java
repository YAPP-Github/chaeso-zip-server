package chaeso.zip.server.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import chaeso.zip.server.support.UserFixture;
import chaeso.zip.server.user.application.dto.UpdateProfileCommand;
import chaeso.zip.server.user.application.dto.UserProfileResponse;
import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserNotFoundException;
import chaeso.zip.server.user.domain.UserRepository;
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

  @Mock
  private UserRepository userRepository;

  private UserServiceImpl userService;

  @BeforeEach
  void setUp() {
    userService = new UserServiceImpl(userRepository);
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
}
