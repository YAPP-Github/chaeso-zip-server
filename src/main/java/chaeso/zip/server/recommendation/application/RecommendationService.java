package chaeso.zip.server.recommendation.application;

import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
import java.util.List;
import java.util.UUID;

public interface RecommendationService {

  /**
   * 온보딩 응답에 맞는 채널을 적합도 순으로 추천한다.
   */
  List<RecommendationItemResponse> recommend(UUID onboardingId);
}
