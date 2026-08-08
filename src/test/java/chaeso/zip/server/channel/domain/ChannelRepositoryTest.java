package chaeso.zip.server.channel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.support.PostgresDataJpaTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
  @DisplayName("unpaged 로 조회하면 활성 채널 전체를 이름순으로 반환한다")
  void searchActiveChannels_unpagedReturnsAll() {
    List<String> activeNames = channelRepository.findAll().stream()
        .filter(Channel::isActive)
        .map(Channel::getName)
        .toList();

    Page<Channel> page =
        channelRepository.searchActiveChannels(null, null, Pageable.unpaged(Sort.by("name")));

    assertThat(page.getContent()).extracting(Channel::getName)
        .containsExactlyInAnyOrderElementsOf(activeNames);
    assertThat(page.getTotalElements()).isEqualTo(activeNames.size());
    assertThat(page.getTotalPages()).isEqualTo(1);
    // 이름순 정렬은 전체 조회에도 적용된다 (정렬 기준은 DB collation)
    assertThat(page.getContent().getFirst().getName())
        .isEqualTo(channelRepository.searchActiveChannels(null, null, PageRequest.of(0, 1, Sort.by("name")))
            .getContent().getFirst().getName());
  }

  @Test
  @DisplayName("page 지정 시에는 요청한 크기만큼만 반환한다")
  void searchActiveChannels_pagedLimitsContent() {
    Page<Channel> page =
        channelRepository.searchActiveChannels(null, null, PageRequest.of(0, 2, Sort.by("name")));

    assertThat(page.getContent()).hasSizeLessThanOrEqualTo(2);
    assertThat(page.getSize()).isEqualTo(2);
  }

  @Test
  @DisplayName("primaryCategory 를 주면 그 업종의 활성 채널만 반환한다")
  void searchActiveChannels_filtersByPrimaryCategory() {
    Category category = activeChannels().getFirst().getPrimaryCategory();
    List<String> expected = namesOf(category);

    Page<Channel> page = channelRepository.searchActiveChannels(
        null, List.of(category), Pageable.unpaged(Sort.by("name")));

    assertThat(page.getContent()).extracting(Channel::getName)
        .containsExactlyInAnyOrderElementsOf(expected);
    assertThat(page.getContent()).allSatisfy(
        channel -> assertThat(channel.getPrimaryCategory()).isEqualTo(category));
    // 업종으로 좁혔으니 전체 조회보다 많을 수 없고, 카운트 쿼리에도 같은 조건이 걸린다
    assertThat(page.getTotalElements()).isEqualTo(expected.size());
  }

  @Test
  @DisplayName("업종을 여러 개 주면 그중 하나에 해당하는 채널을 모두 반환한다")
  void searchActiveChannels_filtersByAnyOfPrimaryCategories() {
    List<Category> categories = activeChannels().stream()
        .map(Channel::getPrimaryCategory)
        .distinct()
        .limit(2)
        .toList();
    assumeTrue(categories.size() == 2, "업종이 둘 이상인 카탈로그가 필요하다");
    List<String> expected = categories.stream().flatMap(category -> namesOf(category).stream())
        .toList();

    Page<Channel> page = channelRepository.searchActiveChannels(
        null, categories, Pageable.unpaged(Sort.by("name")));

    assertThat(page.getContent()).extracting(Channel::getName)
        .containsExactlyInAnyOrderElementsOf(expected);
    assertThat(page.getTotalElements()).isEqualTo(expected.size());
    assertThat(page.getTotalElements()).isGreaterThan(channelRepository
        .searchActiveChannels(null, List.of(categories.getFirst()), Pageable.unpaged())
        .getTotalElements());
  }

  @Test
  @DisplayName("빈 업종 목록은 조건으로 걸지 않아 전체 조회와 같다")
  void searchActiveChannels_emptyCategoriesDoNotFilter() {
    long all = channelRepository.searchActiveChannels(null, null, Pageable.unpaged())
        .getTotalElements();

    assertThat(channelRepository.searchActiveChannels(null, List.of(), Pageable.unpaged())
        .getTotalElements()).isEqualTo(all);
  }

  @Test
  @DisplayName("채널명과 primaryCategory 를 함께 주면 두 조건을 모두 만족하는 채널만 반환한다")
  void searchActiveChannels_combinesNameAndPrimaryCategory() {
    Channel target = activeChannels().getFirst();

    Page<Channel> matched = channelRepository.searchActiveChannels(
        target.getName(), List.of(target.getPrimaryCategory()),
        Pageable.unpaged(Sort.by("name")));
    Page<Channel> mismatched = channelRepository.searchActiveChannels(
        target.getName(), List.of(otherCategoryThan(target.getPrimaryCategory())),
        Pageable.unpaged(Sort.by("name")));

    assertThat(matched.getContent()).extracting(Channel::getName).contains(target.getName());
    assertThat(mismatched.getContent()).extracting(Channel::getName)
        .doesNotContain(target.getName());
  }

  private List<String> namesOf(Category category) {
    return activeChannels().stream()
        .filter(channel -> category.equals(channel.getPrimaryCategory()))
        .map(Channel::getName)
        .toList();
  }

  private List<Channel> activeChannels() {
    return channelRepository.findAll().stream().filter(Channel::isActive).toList();
  }

  /** 대상 채널의 업종이 아닌 아무 업종. 이름이 맞아도 업종이 다르면 걸러지는지 보는 데 쓴다. */
  private Category otherCategoryThan(Category category) {
    return Arrays.stream(Category.values())
        .filter(candidate -> candidate != category)
        .findFirst()
        .orElseThrow();
  }

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
        .filter(p -> !channelPricingRepository.findByChannelProductIdIn(List.of(p.getId())).isEmpty())
        .findFirst()
        .orElseThrow(() -> new AssertionError("단가를 가진 상품이 없습니다"));

    var pricings =
        channelPricingRepository.findByChannelProductIdIn(List.of(productWithPricing.getId()));
    assertThat(pricings).isNotEmpty();
    assertThat(pricings).allSatisfy(pr -> {
      assertThat(pr.getChannelProductId()).isEqualTo(productWithPricing.getId());
      assertThat(pr.getPricingModel()).isNotNull();
    });
  }
}
