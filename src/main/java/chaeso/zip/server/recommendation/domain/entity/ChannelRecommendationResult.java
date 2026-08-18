package chaeso.zip.server.recommendation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "channel_recommendation_results", uniqueConstraints = {
    @UniqueConstraint(name = "uq_channel_recommendation_result_onboarding",
        columnNames = "onboarding_id")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelRecommendationResult {

  @Id
  @UuidGenerator
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "onboarding_id", nullable = false)
  private UUID onboardingId;

  @Column(name = "service_name")
  private String serviceName;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private ChannelRecommendationResult(UUID userId, UUID onboardingId, String serviceName) {
    if (userId == null) {
      throw new IllegalArgumentException("ChannelRecommendationResult requires a userId.");
    }
    if (onboardingId == null) {
      throw new IllegalArgumentException("ChannelRecommendationResult requires an onboardingId.");
    }
    this.userId = userId;
    this.onboardingId = onboardingId;
    this.serviceName = serviceName;
  }
}
