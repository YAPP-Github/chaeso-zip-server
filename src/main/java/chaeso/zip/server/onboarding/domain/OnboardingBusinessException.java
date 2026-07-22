package chaeso.zip.server.onboarding.domain;

import chaeso.zip.server.common.exception.BusinessException;

/**
 * 온보딩 도메인에서 발생하는 비즈니스 예외.
 */
public class OnboardingBusinessException extends BusinessException {

  public OnboardingBusinessException(OnboardingErrorCode errorCode) {
    super(errorCode);
  }

  public OnboardingBusinessException(OnboardingErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  public OnboardingBusinessException(OnboardingErrorCode errorCode, String message,
      Throwable cause) {
    super(errorCode, message, cause);
  }
}
