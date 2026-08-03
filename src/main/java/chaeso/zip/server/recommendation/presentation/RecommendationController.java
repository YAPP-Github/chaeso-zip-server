package chaeso.zip.server.recommendation.presentation;

import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.recommendation.application.RecommendationService;
import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
}
