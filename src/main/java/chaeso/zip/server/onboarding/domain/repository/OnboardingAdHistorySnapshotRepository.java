package chaeso.zip.server.onboarding.domain.repository;

import chaeso.zip.server.onboarding.domain.entity.OnboardingAdHistorySnapshot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingAdHistorySnapshotRepository
    extends JpaRepository<OnboardingAdHistorySnapshot, UUID> {
}
