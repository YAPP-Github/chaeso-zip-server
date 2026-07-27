package chaeso.zip.server.onboarding.application;

import chaeso.zip.server.onboarding.application.dto.OnboardingSubmitResponse;
import chaeso.zip.server.onboarding.application.dto.SubmitOnboardingCommand;
import java.util.UUID;

/**
 * 온보딩 애플리케이션 서비스 인터페이스.
 */
public interface OnboardingService {

  /**
   * 온보딩을 제출한다. 기존 활성 응답이 있으면 비활성으로 내리고 새 응답을 만든다.
   */
  OnboardingSubmitResponse submit(UUID userId, SubmitOnboardingCommand command);
}
