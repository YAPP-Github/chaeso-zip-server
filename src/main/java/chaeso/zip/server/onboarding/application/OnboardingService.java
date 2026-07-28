package chaeso.zip.server.onboarding.application;

import chaeso.zip.server.onboarding.application.dto.OnboardingSubmitResponse;
import chaeso.zip.server.onboarding.application.dto.PresignPerformanceFileCommand;
import chaeso.zip.server.onboarding.application.dto.PresignedFileUploadResult;
import chaeso.zip.server.onboarding.application.dto.SubmitOnboardingCommand;
import java.util.List;
import java.util.UUID;

/**
 * 온보딩 애플리케이션 서비스 인터페이스.
 */
public interface OnboardingService {

  /**
   * 온보딩을 제출한다. userId가 null이면 익명 제출로 저장한다.
   * 로그인 사용자에게 기존 활성 응답이 있으면 비활성으로 내리고 새 응답을 만든다.
   */
  OnboardingSubmitResponse submit(UUID userId, SubmitOnboardingCommand command);

  /**
   * 인증 없이 성과파일 presigned PUT URL을 발급한다.
   */
  List<PresignedFileUploadResult> issuePresignedUrls(List<PresignPerformanceFileCommand> files);
}
