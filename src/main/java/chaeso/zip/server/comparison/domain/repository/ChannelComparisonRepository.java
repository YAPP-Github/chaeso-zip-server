package chaeso.zip.server.comparison.domain.repository;

import chaeso.zip.server.comparison.domain.entity.ChannelComparison;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelComparisonRepository extends JpaRepository<ChannelComparison, UUID> {
}
