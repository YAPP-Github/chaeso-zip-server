package chaeso.zip.server.channel.domain.repository;

import chaeso.zip.server.channel.domain.entity.Channel;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, UUID>, ChannelRepositoryCustom {

}
