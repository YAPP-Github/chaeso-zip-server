package chaeso.zip.server.channel.application;

import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

  private final ChannelRepository channelRepository;

  @Override
  @Transactional(readOnly = true)
  public Page<ChannelListItemResponse> getChannels(String name, Pageable pageable) {
    return channelRepository.searchActiveChannels(name, pageable).map(ChannelListItemResponse::from);
  }
}
