package chaeso.zip.server.sample.application;

import chaeso.zip.server.sample.application.dto.CreateSampleCommand;
import chaeso.zip.server.sample.application.dto.SampleResponse;
import chaeso.zip.server.sample.domain.Sample;
import chaeso.zip.server.sample.domain.SampleNotFoundException;
import chaeso.zip.server.sample.domain.SampleRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SampleService} 구현체. 트랜잭션 경계와 유스케이스 흐름을 담당하며 도메인 객체에 작업을 위임한다.
 *
 * <p>컨벤션:
 * <ul>
 *   <li>클래스 기본은 {@code @Transactional(readOnly = true)}, 쓰기 메서드에만 {@code @Transactional}</li>
 *   <li>생성자 주입({@code @RequiredArgsConstructor} + {@code final})</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SampleServiceImpl implements SampleService {

  private final SampleRepository sampleRepository;

  @Override
  @Transactional
  public SampleResponse create(CreateSampleCommand command) {
    Sample sample = sampleRepository.save(Sample.create(command.name()));
    return SampleResponse.from(sample);
  }

  @Override
  public SampleResponse getById(UUID id) {
    Sample sample = sampleRepository.findById(id)
        .orElseThrow(() -> new SampleNotFoundException(id));
    return SampleResponse.from(sample);
  }

  @Override
  public List<SampleResponse> getAll() {
    return sampleRepository.findAll().stream()
        .map(SampleResponse::from)
        .toList();
  }
}
