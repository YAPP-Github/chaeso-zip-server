package chaeso.zip.server.onboarding.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.onboarding.application.OnboardingService;
import chaeso.zip.server.onboarding.application.dto.OnboardingSubmitResponse;
import chaeso.zip.server.onboarding.presentation.dto.SubmitOnboardingRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 온보딩 REST API. 컨트롤러는 요청/응답 변환만 담당한다.
 * 모든 응답은 {@link ApiResponse} 로 감싼다.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController implements OnboardingApiDocs {

  private final OnboardingService onboardingService;

  @Override
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<OnboardingSubmitResponse> submit(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody SubmitOnboardingRequest request) {
    return ApiResponse.success(onboardingService.submit(principal.userId(), request.toCommand()));
  }
}
