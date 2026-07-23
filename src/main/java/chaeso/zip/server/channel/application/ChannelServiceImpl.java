package chaeso.zip.server.channel.application;

import chaeso.zip.server.channel.application.dto.AudienceMetricResponse;
import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.channel.application.dto.PricingResponse;
import chaeso.zip.server.channel.application.dto.ProductResponse;
import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelReference;
import chaeso.zip.server.channel.domain.repository.ChannelAudienceMetricRepository;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelReferenceRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

  private final ChannelRepository channelRepository;
  private final ChannelProductRepository channelProductRepository;
  private final ChannelPricingRepository channelPricingRepository;
  private final ChannelAudienceMetricRepository channelAudienceMetricRepository;
  private final ChannelReferenceRepository channelReferenceRepository;

  @Override
  @Transactional(readOnly = true)
  public Page<ChannelListItemResponse> getChannels(String name, Pageable pageable) {
    return channelRepository.searchActiveChannels(name, pageable).map(ChannelListItemResponse::from);
  }

  @Override
  @Transactional(readOnly = true)
  public ChannelDetailResponse getChannel(UUID id) {
    Channel channel = channelRepository.findById(id)
        .orElseThrow(() -> new ChannelNotFoundException(id));

    List<ProductResponse> products = channelProductRepository.findByChannelId(id).stream()
        .map(product -> ProductResponse.from(product, pricingOf(product.getId())))
        .toList();

    List<AudienceMetricResponse> audienceMetrics =
        channelAudienceMetricRepository.findByChannelId(id).stream()
            .map(AudienceMetricResponse::from)
            .toList();

    List<String> references = channelReferenceRepository.findByChannelId(id).stream()
        .map(ChannelReference::getResultText)
        .filter(Objects::nonNull)
        .toList();

    return ChannelDetailResponse.from(channel, products, audienceMetrics, references);
  }

  private List<PricingResponse> pricingOf(UUID channelProductId) {
    return channelPricingRepository.findByChannelProductId(channelProductId).stream()
        .map(PricingResponse::from)
        .toList();
  }
}
