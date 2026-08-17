package chaeso.zip.server.comparison.domain.repository;

import chaeso.zip.server.comparison.domain.entity.ChannelComparisonItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelComparisonItemRepository
    extends JpaRepository<ChannelComparisonItem, UUID> {

  List<ChannelComparisonItem> findByComparisonIdInOrderBySortOrderAsc(List<UUID> comparisonIds);
}
