package chaeso.zip.server.common.entity;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

  @Id
  @UuidGenerator
  private UUID id;
}
