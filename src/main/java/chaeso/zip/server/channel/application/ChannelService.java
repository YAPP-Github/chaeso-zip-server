package chaeso.zip.server.channel.application;

import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.channel.domain.vo.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChannelService {

  Page<ChannelListItemResponse> getChannels(String name, List<Category> primaryCategories,
      Pageable pageable);

  ChannelDetailResponse getChannel(UUID id, UUID onboardingId, UUID requesterId);
}
