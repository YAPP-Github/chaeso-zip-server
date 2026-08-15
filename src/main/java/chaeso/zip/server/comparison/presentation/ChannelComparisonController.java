package chaeso.zip.server.comparison.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.comparison.application.ChannelComparisonService;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonResponse;
import chaeso.zip.server.comparison.presentation.dto.ChannelComparisonRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채널 비교 REST API.
 */
@RestController
@RequestMapping("/api/v1/channel-comparisons")
@RequiredArgsConstructor
public class ChannelComparisonController implements ChannelComparisonApiDocs {

  private final ChannelComparisonService channelComparisonService;

  @Override
  @GetMapping
  public ApiResponse<ChannelComparisonResponse> getChannelComparison(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @ModelAttribute ChannelComparisonRequest request) {
    return ApiResponse.success(channelComparisonService.compare(
        request.channelIds(), request.onboardingId(),
        principal == null ? null : principal.userId()));
  }
}
