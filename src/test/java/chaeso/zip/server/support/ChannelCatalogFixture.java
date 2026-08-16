package chaeso.zip.server.support;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.Gender;
import chaeso.zip.server.channel.domain.vo.PriceType;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

public final class ChannelCatalogFixture {

  private ChannelCatalogFixture() {
  }

  public static Channel persistableChannel(String name) {
    Channel channel = BeanUtils.instantiateClass(Channel.class);
    set(channel, "name", name);
    return channel;
  }

  public static Channel channel(UUID id, String name) {
    Channel channel = BeanUtils.instantiateClass(Channel.class);
    set(channel, "id", id);
    set(channel, "name", name);
    set(channel, "active", true);
    return channel;
  }

  public static Channel channelWithDefaultTags(UUID id, String name, List<String> defaultTags) {
    Channel channel = channel(id, name);
    set(channel, "defaultTags", defaultTags);
    return channel;
  }

  /** 추천 매칭 축(적합 업종·연령대)과 주요 타깃까지 채운 채널. */
  public static Channel channel(UUID id, String name, List<Category> suitableCategories,
      List<AgeBand> ageBandCodes, String primaryAgeBand, Gender primaryGender) {
    Channel channel = channel(id, name);
    set(channel, "suitableCategories", suitableCategories);
    set(channel, "ageBandCodes", ageBandCodes);
    set(channel, "primaryAgeBand", primaryAgeBand);
    set(channel, "primaryGender", primaryGender);
    return channel;
  }

  /** 상품이 지원하는 광고 목적을 지정한다. */
  public static ChannelProduct withObjectives(ChannelProduct product,
      CampaignObjective... objectives) {
    set(product, "supportedObjectives", List.of(objectives));
    return product;
  }

  /** CTR·노출 정보가 전혀 없는 상품. */
  public static ChannelProduct product(UUID id, UUID channelId) {
    return product(id, channelId, null, null, null);
  }

  public static ChannelProduct product(UUID id, UUID channelId, BigDecimal ctr,
      Long expectedImpressions, String expectedPeriod) {
    ChannelProduct product = BeanUtils.instantiateClass(ChannelProduct.class);
    set(product, "id", id);
    set(product, "channelId", channelId);
    set(product, "ctr", ctr);
    set(product, "expectedImpressions", expectedImpressions);
    set(product, "expectedPeriod", expectedPeriod);
    return product;
  }

  /** 최소 집행 금액이 등록된 상품 */
  public static ChannelProduct productWithMinBudget(UUID id, UUID channelId, int minBudgetWon) {
    ChannelProduct product = product(id, channelId);
    set(product, "minBudgetWon", minBudgetWon);
    return product;
  }

  /** 단일 CTR 없이 구간(min/max)만 가진 상품. */
  public static ChannelProduct productWithCtrRange(UUID id, UUID channelId, String ctrMin,
      String ctrMax) {
    ChannelProduct product = product(id, channelId);
    set(product, "ctrMin", new BigDecimal(ctrMin));
    set(product, "ctrMax", new BigDecimal(ctrMax));
    return product;
  }

  /** 공시가 단가. */
  public static ChannelPricing pricing(UUID channelProductId, PricingModel pricingModel,
      String value) {
    return pricing(channelProductId, pricingModel, PriceType.LIST, value, null, null);
  }

  public static ChannelPricing pricing(UUID channelProductId, PricingModel pricingModel,
      PriceType priceType, String value, String valueMax, String unitDays) {
    ChannelPricing pricing = BeanUtils.instantiateClass(ChannelPricing.class);
    set(pricing, "id", UUID.randomUUID());
    set(pricing, "channelProductId", channelProductId);
    set(pricing, "pricingModel", pricingModel);
    set(pricing, "priceType", priceType);
    set(pricing, "value", decimal(value));
    set(pricing, "valueMax", decimal(valueMax));
    set(pricing, "unitDays", decimal(unitDays));
    return pricing;
  }

  private static BigDecimal decimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  private static void set(Object target, String field, Object value) {
    ReflectionTestUtils.setField(target, field, value);
  }
}
