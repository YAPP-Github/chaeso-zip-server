package chaeso.zip.server.channel.application;

import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChannelService {

  Page<ChannelListItemResponse> getChannels(String name, Pageable pageable);
}
