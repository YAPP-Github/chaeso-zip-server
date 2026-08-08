package chaeso.zip.server.channel.domain.repository;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.vo.Category;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChannelRepositoryCustom {

  Page<Channel> searchActiveChannels(String name, List<Category> primaryCategories,
      Pageable pageable);
}
