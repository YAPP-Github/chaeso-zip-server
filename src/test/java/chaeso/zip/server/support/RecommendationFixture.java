package chaeso.zip.server.support;

import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendation;
import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendationResult;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

public final class RecommendationFixture {

  private RecommendationFixture() {
  }

  public static ChannelRecommendationResult result(UUID id, UUID userId, UUID onboardingId,
      String serviceName, LocalDateTime createdAt) {
    ChannelRecommendationResult result =
        BeanUtils.instantiateClass(ChannelRecommendationResult.class);
    set(result, "id", id);
    set(result, "userId", userId);
    set(result, "onboardingId", onboardingId);
    set(result, "serviceName", serviceName);
    set(result, "createdAt", createdAt);
    return result;
  }

  public static ChannelRecommendation recommendation(UUID resultId, int rank, String channelName) {
    ChannelRecommendation recommendation = BeanUtils.instantiateClass(ChannelRecommendation.class);
    set(recommendation, "id", UUID.randomUUID());
    set(recommendation, "resultId", resultId);
    set(recommendation, "rank", rank);
    set(recommendation, "channelName", channelName);
    return recommendation;
  }

  private static void set(Object target, String field, Object value) {
    ReflectionTestUtils.setField(target, field, value);
  }
}
