package chaeso.zip.server.support;

import chaeso.zip.server.common.config.JpaAuditingConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 로컬 PostgreSQL DB 기반 DataJpaTest 통합 테스트용 합성 어노테이션.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/chaeso_zip",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.datasource.username=postgres",
    "spring.datasource.password=postgres",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=false"
})
@EnabledIf("chaeso.zip.server.support.PostgresCondition#postgresAvailable")
public @interface PostgresDataJpaTest {
}
