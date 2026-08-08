package chaeso.zip.server.user.application;

import chaeso.zip.server.user.application.dto.UpdateProfileCommand;
import chaeso.zip.server.user.application.dto.UserProfileResponse;
import java.util.UUID;

public interface UserService {

  /**
   * 사용자의 이메일/이름/직군/회사명을 조회한다.
   */
  UserProfileResponse getMyProfile(UUID userId);

  /**
   * 사용자의 회사명/직무를 수정한다.
   */
  UserProfileResponse updateMyProfile(UUID userId, UpdateProfileCommand command);
}
