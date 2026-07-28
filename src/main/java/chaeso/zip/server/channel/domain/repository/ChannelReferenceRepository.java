package chaeso.zip.server.channel.domain.repository;

import chaeso.zip.server.channel.domain.entity.ChannelReference;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelReferenceRepository extends JpaRepository<ChannelReference, UUID> {

  List<ChannelReference> findByChannelId(UUID channelId);
}
