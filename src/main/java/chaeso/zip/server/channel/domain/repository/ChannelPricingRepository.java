package chaeso.zip.server.channel.domain.repository;

import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelPricingRepository extends JpaRepository<ChannelPricing, UUID> {
}
