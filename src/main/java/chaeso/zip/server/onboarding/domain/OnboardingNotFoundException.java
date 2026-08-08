package chaeso.zip.server.onboarding.domain;

import chaeso.zip.server.common.exception.BusinessException;
import java.util.UUID;

public class OnboardingNotFoundException extends BusinessException {

  public OnboardingNotFoundException(UUID id) {
    super(OnboardingErrorCode.ONBOARDING_NOT_FOUND, "온보딩 정보가 없습니다. id=" + id);
  }
}
