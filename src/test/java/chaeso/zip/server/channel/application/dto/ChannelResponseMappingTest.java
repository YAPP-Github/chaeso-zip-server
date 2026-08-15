package chaeso.zip.server.channel.application.dto;

import static chaeso.zip.server.support.ChannelCatalogFixture.channel;
import static chaeso.zip.server.support.ChannelCatalogFixture.product;
import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChannelResponseMappingTest {

  @Test
  @DisplayName("소개서에 목록 정보가 없는 채널은 null 이 아니라 빈 배열을 준다")
  void mapsMissingChannelListsToEmpty() {
    Channel channel = channel(UUID.randomUUID(), "정보 없는 채널");

    ChannelDetailResponse detail =
        ChannelDetailResponse.from(channel, List.of(), List.of(), List.of(), null);

    assertThat(detail.suitableCategories()).isEmpty();
    assertThat(detail.ageBandCodes()).isEmpty();
    assertThat(detail.advantages()).isEmpty();
    assertThat(detail.adFormats()).isEmpty();
    assertThat(detail.targetingMethods()).isEmpty();
  }

  @Test
  @DisplayName("tagline 은 채널 값을 그대로 싣고, 없으면 null 이다")
  void mapsTagline() {
    Channel withTagline = taggedChannel("월 방문자 수 상위 오픈마켓");
    Channel withoutTagline = channel(UUID.randomUUID(), "한 줄 없는 채널");

    assertThat(detailOf(withTagline).tagline()).isEqualTo("월 방문자 수 상위 오픈마켓");
    assertThat(detailOf(withoutTagline).tagline()).isNull();
  }

  @Test
  @DisplayName("지원 광고 목표가 없는 상품은 null 이 아니라 빈 배열을 준다")
  void mapsMissingProductObjectivesToEmpty() {
    ChannelProduct channelProduct = product(UUID.randomUUID(), UUID.randomUUID());

    ProductResponse product = ProductResponse.from(channelProduct, List.of(), null, null);

    assertThat(product.supportedObjectives()).isEmpty();
  }

  private static ChannelDetailResponse detailOf(Channel channel) {
    return ChannelDetailResponse.from(channel, List.of(), List.of(), List.of(), null);
  }

  private static Channel taggedChannel(String tagline) {
    Channel channel = channel(UUID.randomUUID(), "11번가 광고");
    ReflectionTestUtils.setField(channel, "tagline", tagline);
    return channel;
  }
}
