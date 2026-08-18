package chaeso.zip.server.recommendation.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.PageResponse;
import chaeso.zip.server.recommendation.application.RecommendationService;
import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
import chaeso.zip.server.recommendation.application.dto.RecommendationSummaryResponse;
import chaeso.zip.server.recommendation.application.dto.SavedRecommendationResponse;
import chaeso.zip.server.recommendation.presentation.dto.RecommendationPageRequest;
import chaeso.zip.server.recommendation.presentation.dto.SaveRecommendationRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController implements RecommendationApiDocs {

  private final RecommendationService recommendationService;

  @Override
  @GetMapping
  public ApiResponse<List<RecommendationItemResponse>> getRecommendations(
      @RequestParam UUID onboardingId) {
    return ApiResponse.success(recommendationService.recommend(onboardingId));
  }

  @Override
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SavedRecommendationResponse> saveRecommendation(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody SaveRecommendationRequest request) {
    return ApiResponse.success(recommendationService.save(
        principal.userId(), request.onboardingId(), request.serviceName()));
  }

  @Override
  @GetMapping("/my")
  public ApiResponse<PageResponse<RecommendationSummaryResponse>> getMyRecommendations(
      @AuthenticationPrincipal UserPrincipal principal,
      @ParameterObject RecommendationPageRequest request) {
    return ApiResponse.success(PageResponse.from(
        recommendationService.findMyRecommendations(principal.userId(), request.toPageable())));
  }
}
