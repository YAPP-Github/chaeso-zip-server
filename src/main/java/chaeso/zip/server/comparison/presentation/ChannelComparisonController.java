package chaeso.zip.server.comparison.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.comparison.application.ChannelComparisonService;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonResponse;
import chaeso.zip.server.comparison.application.dto.SavedChannelComparisonResponse;
import chaeso.zip.server.comparison.presentation.dto.ChannelComparisonRequest;
import chaeso.zip.server.comparison.presentation.dto.SaveChannelComparisonRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

  @Override
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SavedChannelComparisonResponse> saveChannelComparison(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody SaveChannelComparisonRequest request) {
    return ApiResponse.success(channelComparisonService.save(principal.userId(),
        request.channelIds(), request.onboardingId(), request.serviceName()));
  }

  @Override
  @GetMapping("/{comparisonId}")
  public ApiResponse<SavedChannelComparisonResponse> getSavedChannelComparison(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID comparisonId) {
    return ApiResponse.success(
        channelComparisonService.findComparison(principal.userId(), comparisonId));
  }
}
