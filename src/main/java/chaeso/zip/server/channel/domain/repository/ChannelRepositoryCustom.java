package chaeso.zip.server.channel.domain.repository;

import chaeso.zip.server.channel.domain.entity.Channel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChannelRepositoryCustom {

  Page<Channel> searchActiveChannels(String name, Pageable pageable);
}
