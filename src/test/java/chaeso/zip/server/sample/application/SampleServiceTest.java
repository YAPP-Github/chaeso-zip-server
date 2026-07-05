package chaeso.zip.server.sample.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import chaeso.zip.server.sample.application.dto.CreateSampleCommand;
import chaeso.zip.server.sample.application.dto.SampleResponse;
import chaeso.zip.server.sample.domain.Sample;
import chaeso.zip.server.sample.domain.SampleNotFoundException;
import chaeso.zip.server.sample.domain.SampleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 애플리케이션 서비스 단위 테스트 컨벤션. 인프라 의존성은 Mockito 로 대체하고 유스케이스 흐름만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class SampleServiceTest {

  @Mock
  private SampleRepository sampleRepository;

  @InjectMocks
  private SampleServiceImpl sampleService;

  @Test
  @DisplayName("샘플을 생성하면 저장 후 응답을 반환한다")
  void create() {
    given(sampleRepository.save(any(Sample.class))).willAnswer(invocation -> invocation.getArgument(0));

    SampleResponse response = sampleService.create(new CreateSampleCommand("채소"));

    assertThat(response.name()).isEqualTo("채소");
  }

  @Test
  @DisplayName("존재하지 않는 샘플을 조회하면 예외가 발생한다")
  void getById_notFound() {
    UUID id = UUID.randomUUID();
    given(sampleRepository.findById(id)).willReturn(Optional.empty());

    assertThatThrownBy(() -> sampleService.getById(id))
        .isInstanceOf(SampleNotFoundException.class);
  }
}
