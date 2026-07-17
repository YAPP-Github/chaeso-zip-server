package chaeso.zip.server.channel.domain.repository;

import chaeso.zip.server.channel.domain.entity.ChannelReference;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelReferenceRepository extends JpaRepository<ChannelReference, UUID> {
}
