package chaeso.zip.server.recommendation.application;

import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
import chaeso.zip.server.recommendation.application.dto.SavedRecommendationResponse;
import java.util.List;
import java.util.UUID;

public interface RecommendationService {

  /**
   * 온보딩 응답에 맞는 채널을 적합도 순으로 추천한다.
   */
  List<RecommendationItemResponse> recommend(UUID onboardingId);

  /**
   * 추천 결과를 추천 시점 값 그대로 저장한다. 같은 온보딩으로 다시 저장하면 이전 것을 덮어쓴다.
   *
   * @param userId       저장하는 사용자
   * @param onboardingId 추천의 근거가 된 온보딩. 저장된 추천 1건을 가리키는 키가 된다
   * @param serviceName  저장 요청시 입력받은 서비스명
   */
  SavedRecommendationResponse save(UUID userId, UUID onboardingId, String serviceName);
}
