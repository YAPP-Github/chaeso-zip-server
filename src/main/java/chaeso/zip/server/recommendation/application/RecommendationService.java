package chaeso.zip.server.recommendation.application;

import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
import chaeso.zip.server.recommendation.application.dto.RecommendationSummaryResponse;
import chaeso.zip.server.recommendation.application.dto.SavedRecommendationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RecommendationService {

  /**
   * 온보딩 응답에 맞는 채널을 적합도 순으로 추천한다.
   */
  List<RecommendationItemResponse> recommend(UUID onboardingId);

  /**
   * 추천 결과를 추천 시점 값 그대로 저장한다. 같은 온보딩으로 다시 저장하면 이전 것을 덮어쓴다.
   *
   * @param userId       저장하는 사용자
   * @param onboardingId 추천의 근거가 된 온보딩.
   * @param serviceName  저장 요청시 입력받은 서비스명
   * @return 저장된 추천
   */
  SavedRecommendationResponse save(UUID userId, UUID onboardingId, String serviceName);

  /**
   * 로그인한 사용자가 저장한 추천 결과를 최신순으로 조회한다.
   *
   * @param userId   조회하는 사용자
   * @param pageable 페이지 요청(최신순 고정)
   * @return 추천 목록 요약
   */
  Page<RecommendationSummaryResponse> findMyRecommendations(UUID userId, Pageable pageable);
}
