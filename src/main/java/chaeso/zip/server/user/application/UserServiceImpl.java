package chaeso.zip.server.user.application;

import chaeso.zip.server.user.application.dto.UpdateProfileCommand;
import chaeso.zip.server.user.application.dto.UserProfileResponse;
import chaeso.zip.server.user.domain.User;
import chaeso.zip.server.user.domain.UserNotFoundException;
import chaeso.zip.server.user.domain.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;

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

  private User findUser(UUID userId) {
    return userRepository.findByIdAndDeletedAtIsNull(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
  }
}
