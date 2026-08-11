package chaeso.zip.server.user.domain;

import chaeso.zip.server.common.exception.BusinessException;
import java.util.UUID;

public class UserNotFoundException extends BusinessException {

  public UserNotFoundException(UUID id) {
    super(UserErrorCode.USER_NOT_FOUND, "존재하지 않는 회원입니다. id=" + id);
  }
}
