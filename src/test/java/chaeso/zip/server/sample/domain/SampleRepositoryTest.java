package chaeso.zip.server.sample.domain;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.common.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 영속성 계층 테스트 컨벤션. {@code @DataJpaTest} 는 Auditing 설정을 스캔하지 않으므로
 * {@link JpaAuditingConfig} 를 명시적으로 import 한다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
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
