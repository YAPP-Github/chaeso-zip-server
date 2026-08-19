package chaeso.zip.server.recommendation.domain;

import chaeso.zip.server.common.exception.BusinessException;
import java.util.UUID;

public class RecommendationNotFoundException extends BusinessException {

  public RecommendationNotFoundException(UUID recommendationId) {
    super(RecommendationErrorCode.RECOMMENDATION_NOT_FOUND,
        "존재하지 않는 추천입니다. id=" + recommendationId);
  }
}
