package chaeso.zip.server.support;

import chaeso.zip.server.common.config.JpaAuditingConfig;
import chaeso.zip.server.common.config.QuerydslConfig;
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
 * {@code @DataJpaTest} 는 커스텀 리포지토리 프래그먼트까지 모두 로드하므로, QueryDSL 을 쓰지 않는
 * 슬라이스라도 {@link QuerydslConfig} 가 없으면 JPAQueryFactory 빈 부재로 컨텍스트 로딩이 실패한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
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
