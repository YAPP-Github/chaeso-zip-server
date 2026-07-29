package chaeso.zip.server.channel.domain.repository;

import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChannelProductRepository extends JpaRepository<ChannelProduct, UUID> {

  List<ChannelProduct> findByChannelId(UUID channelId);

  List<ChannelProduct> findByChannelIdIn(Collection<UUID> channelIds);

  /**
   * CTR 이 명시된 전체 상품의 평균 CTR(%). 단일값이 없으면 구간(min/max) 평균을 쓴다.
   *
   * @return 평균 CTR(%). CTR 을 가진 상품이 하나도 없으면 {@code null}
   */
  @Query("""
      select avg(coalesce(product.ctr, (product.ctrMin + product.ctrMax) / 2))
      from ChannelProduct product
      where product.ctr is not null
         or (product.ctrMin is not null and product.ctrMax is not null)
      """)
  Double findAverageCtrPercent();
}
