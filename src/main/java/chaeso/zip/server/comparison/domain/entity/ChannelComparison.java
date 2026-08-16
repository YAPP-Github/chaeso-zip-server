package chaeso.zip.server.comparison.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "channel_comparisons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelComparison {

  @Id
  @UuidGenerator
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "onboarding_id")
  private UUID onboardingId;

  @Column(name = "service_name")
  private String serviceName;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private ChannelComparison(UUID userId, UUID onboardingId, String serviceName) {
    if (userId == null) {
      throw new IllegalArgumentException("ChannelComparison requires a userId.");
    }
    this.userId = userId;
    this.onboardingId = onboardingId;
    this.serviceName = serviceName;
  }
}
