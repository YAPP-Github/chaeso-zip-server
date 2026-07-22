package chaeso.zip.server.sample.domain;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.common.config.JpaAuditingConfig;
import chaeso.zip.server.common.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 영속성 계층 테스트 컨벤션. {@code @DataJpaTest} 는 일반 {@code @Configuration} 을 스캔하지 않으므로
 * Auditing 설정({@link JpaAuditingConfig})과 QueryDSL 설정({@link QuerydslConfig})을 명시적으로 import 한다.
 * ({@code @DataJpaTest} 는 커스텀 리포지토리 프래그먼트까지 모두 로드하므로, QueryDSL 을 쓰지 않는 슬라이스라도
 * {@link QuerydslConfig} 가 없으면 {@code JPAQueryFactory} 빈 부재로 컨텍스트 로딩이 실패한다.)
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class SampleRepositoryTest {

  @Autowired
  private SampleRepository sampleRepository;

  @Test
  @DisplayName("저장하면 식별자와 생성/수정 시각이 자동으로 채워진다")
  void save_fillsAuditingFields() {
    Sample saved = sampleRepository.save(Sample.create("채소"));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }
}
