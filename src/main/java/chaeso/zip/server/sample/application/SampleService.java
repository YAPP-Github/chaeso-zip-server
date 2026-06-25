package chaeso.zip.server.sample.application;

import chaeso.zip.server.sample.application.dto.CreateSampleCommand;
import chaeso.zip.server.sample.application.dto.SampleResponse;
import java.util.List;

/**
 * 샘플 애플리케이션 서비스. 유스케이스 진입점(인터페이스)으로, 구현은 {@link SampleServiceImpl} 가 담당한다.
 *
 * <p>컨벤션:
 * <ul>
 *   <li>표현 계층은 인터페이스에만 의존하고 구현체를 직접 참조하지 않는다</li>
 *   <li>외부에는 엔티티가 아닌 응답 DTO 를 반환</li>
 * </ul>
 */
public interface SampleService {

  /**
   * 커맨드를 받아 새로운 샘플을 생성한다.
   */
  SampleResponse create(CreateSampleCommand command);

  /**
   * 식별자로 샘플을 단건 조회한다.
   */
  SampleResponse getById(Long id);

  /**
   * 전체 샘플 목록을 조회한다.
   */
  List<SampleResponse> getAll();
}
