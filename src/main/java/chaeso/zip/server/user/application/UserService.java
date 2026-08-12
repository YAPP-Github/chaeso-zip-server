package chaeso.zip.server.user.application;

import chaeso.zip.server.user.application.dto.UpdateProfileCommand;
import chaeso.zip.server.user.application.dto.UserProfileResponse;
import chaeso.zip.server.user.application.dto.WithdrawalResponse;
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

  /** 인증된 사용자를 탈퇴 처리하고 탈퇴 시각을 반환한다. */
  WithdrawalResponse withdraw(UUID userId, int sessionVersion);
}
