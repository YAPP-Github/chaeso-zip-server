package chaeso.zip.server.channel.domain.repository;

import chaeso.zip.server.channel.domain.entity.ChannelAudienceMetric;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelAudienceMetricRepository extends JpaRepository<ChannelAudienceMetric, UUID> {

  List<ChannelAudienceMetric> findByChannelId(UUID channelId);
}
