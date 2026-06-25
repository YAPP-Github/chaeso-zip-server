package chaeso.zip.server.sample.application.dto;

import chaeso.zip.server.sample.domain.Sample;
import java.time.LocalDateTime;

/**
 * 샘플 조회 응답 DTO. 도메인 엔티티를 외부로 직접 노출하지 않기 위한 변환 객체.
 */
public record SampleResponse(
    Long id,
    String name,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static SampleResponse from(Sample sample) {
    return new SampleResponse(
        sample.getId(),
        sample.getName(),
        sample.getCreatedAt(),
        sample.getUpdatedAt());
  }
}
