package chaeso.zip.server.channel.domain.repository;

import chaeso.zip.server.channel.domain.entity.SourceDocument;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, UUID> {
}
