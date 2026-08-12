package chaeso.zip.server.user.application;

import chaeso.zip.server.auth.domain.InvalidTokenException;
import chaeso.zip.server.user.application.dto.UpdateProfileCommand;
import chaeso.zip.server.user.application.dto.UserProfileResponse;
import chaeso.zip.server.user.application.dto.WithdrawalResponse;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserNotFoundException;
import chaeso.zip.server.user.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final Clock clock;

  @Override
  public UserProfileResponse getMyProfile(UUID userId) {
    return UserProfileResponse.from(findUser(userId));
  }

  @Override
  @Transactional
  public UserProfileResponse updateMyProfile(UUID userId, UpdateProfileCommand command) {
    User user = findUser(userId);
    user.updateProfile(command.companyName(), command.occupation());
    return UserProfileResponse.from(user);
  }

  @Override
  @Transactional
  public WithdrawalResponse withdraw(UUID userId, int sessionVersion) {
    User user = userRepository.findByIdForUpdate(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    if (user.getSessionVersion() != sessionVersion) {
      throw new InvalidTokenException("Access Token 세션이 만료되었습니다.");
    }

    LocalDateTime now = LocalDateTime.now(clock);
    user.withdraw(now);
    return WithdrawalResponse.from(now);
  }

  private User findUser(UUID userId) {
    return userRepository.findByIdAndDeletedAtIsNull(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
  }
}
