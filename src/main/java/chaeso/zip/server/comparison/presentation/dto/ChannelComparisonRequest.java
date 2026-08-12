package chaeso.zip.server.comparison.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 채널 비교 조회 쿼리 파라미터 DTO.
 */
@Schema(description = "채널 비교 조회 요청")
public record ChannelComparisonRequest(
    @Schema(description = "비교할 채널 식별자 목록. 1개 이상 3개 이하이며, 전달한 순서대로 결과를 반환한다",
        example = "[\"550e8400-e29b-41d4-a716-446655440000\", \"9c1e8c2a-3f4d-4a5b-9c6d-7e8f9a0b1c2e\"]",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "비교할 채널을 1개 이상 선택해 주세요")
    @Size(max = 3, message = "비교할 채널은 최대 3개까지 선택할 수 있습니다")
    List<UUID> channelIds,

    @Schema(description = "온보딩 기반 비교에 사용할 온보딩 응답 식별자. 생략하면 일반 채널 비교로 조회한다",
        example = "550e8400-e29b-41d4-a716-446655440000")
    UUID onboardingId) {

  /**
   * 같은 채널은 한 번만 비교할 수 있다.
   */
  @JsonIgnore
  @AssertTrue(message = "같은 채널을 중복해서 비교할 수 없습니다")
  public boolean isChannelIdsDistinct() {
    if (channelIds == null) {
      return true;
    }
    List<UUID> nonNullChannelIds = channelIds.stream()
        .filter(Objects::nonNull)
        .toList();
    return nonNullChannelIds.size() == Set.copyOf(nonNullChannelIds).size();
  }
}
