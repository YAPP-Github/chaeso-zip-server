package chaeso.zip.server.sample.application.dto;

import chaeso.zip.server.sample.domain.Sample;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 샘플 조회 응답 DTO. 도메인 엔티티를 외부로 직접 노출하지 않기 위한 변환 객체.
 */
@Schema(description = "샘플 응답")
public record SampleResponse(
    @Schema(description = "샘플 식별자", example = "1")
    Long id,
    @Schema(description = "샘플 이름", example = "채소")
    String name,
    @Schema(description = "생성 시각")
    LocalDateTime createdAt,
    @Schema(description = "수정 시각")
    LocalDateTime updatedAt) {

  public static SampleResponse from(Sample sample) {
    return new SampleResponse(
        sample.getId(),
        sample.getName(),
        sample.getCreatedAt(),
        sample.getUpdatedAt());
  }
}
