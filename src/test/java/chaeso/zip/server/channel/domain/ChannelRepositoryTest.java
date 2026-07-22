package chaeso.zip.server.channel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.support.PostgresDataJpaTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 채널 카탈로그 특수 컬럼 매핑을 실제 적재된 데이터로 검증하는 통합 테스트
 */
@PostgresDataJpaTest
class ChannelRepositoryTest {

  @Autowired
  private ChannelRepository channelRepository;
  @Autowired
  private ChannelProductRepository channelProductRepository;
  @Autowired
  private ChannelPricingRepository channelPricingRepository;

  @Test
  @DisplayName("전체 채널·상품·단가의 배열/단일 enum 이 예외 없이 매핑된다 (빈 배열·null 포함)")
  void arraysAndEnumsMapForAllRows() {
    List<Channel> channels = channelRepository.findAll();
    assertThat(channels).isNotEmpty();
    assertThat(channels).allSatisfy(c -> assertThat(c.getPrimaryCategory()).isNotNull());   // primary_category 전부 채워짐
    assertThat(channels).anySatisfy(c -> assertThat(c.getSuitableCategories()).isNotEmpty()); // 값 있는 배열
    assertThat(channels).anySatisfy(c -> assertThat(c.getAgeBandCodes()).isEmpty());        // 빈 배열 채널
    assertThat(channels).anySatisfy(c -> assertThat(c.getPrimaryGender()).isNull());        // null 단일 enum 채널

    var products = channelProductRepository.findAll();
    assertThat(products).isNotEmpty();
    assertThat(products).anySatisfy(p -> assertThat(p.getSupportedObjectives()).isNotEmpty()); // 값 있는 배열

    var pricings = channelPricingRepository.findAll();
    assertThat(pricings).isNotEmpty();
    assertThat(pricings).allSatisfy(pr -> {   // not-null enum 컬럼 4종이 값으로 매핑됨
      assertThat(pr.getPricingModel()).isNotNull();
      assertThat(pr.getPriceType()).isNotNull();
      assertThat(pr.getVat()).isNotNull();
      assertThat(pr.getCurrency()).isNotNull();
    });
  }

  @Test
  @DisplayName("id 기반 조회 체인이 동작한다: channel → products → pricing")
  void idBasedLookupChain() {
    Channel channelWithProducts = channelRepository.findAll().stream()
        .filter(c -> !channelProductRepository.findByChannelId(c.getId()).isEmpty())
        .findFirst()
        .orElseThrow(() -> new AssertionError("상품을 가진 채널이 없습니다"));

    List<ChannelProduct> products =
        channelProductRepository.findByChannelId(channelWithProducts.getId());
    assertThat(products).isNotEmpty();
    assertThat(products).allSatisfy(p -> assertThat(p.getChannelId()).isEqualTo(channelWithProducts.getId()));

    ChannelProduct productWithPricing = products.stream()
        .filter(p -> !channelPricingRepository.findByChannelProductId(p.getId()).isEmpty())
        .findFirst()
        .orElseThrow(() -> new AssertionError("단가를 가진 상품이 없습니다"));

    var pricings = channelPricingRepository.findByChannelProductId(productWithPricing.getId());
    assertThat(pricings).isNotEmpty();
    assertThat(pricings).allSatisfy(pr -> {
      assertThat(pr.getChannelProductId()).isEqualTo(productWithPricing.getId());
      assertThat(pr.getPricingModel()).isNotNull();
    });
  }
}
