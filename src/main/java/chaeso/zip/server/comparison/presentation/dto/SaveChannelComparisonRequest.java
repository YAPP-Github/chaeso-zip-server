package chaeso.zip.server.comparison.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Schema(description = "채널 비교 결과 저장 요청")
public record SaveChannelComparisonRequest(
    @Schema(description = "비교할 채널 식별자 목록. 2개 이상 3개 이하",
        example = "[\"550e8400-e29b-41d4-a716-446655440000\", \"9c1e8c2a-3f4d-4a5b-9c6d-7e8f9a0b1c2e\"]",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "비교할 채널을 2개 이상 선택해 주세요")
    @Size(min = 2, max = 3, message = "비교할 채널은 2개 이상 3개 이하로 선택해 주세요")
    List<@NotNull UUID> channelIds,

    @Schema(description = "저장 근거가 된 온보딩 응답 식별자(선택)",
        example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    UUID onboardingId,

    @Schema(description = "onboardingId 없을 때만 쓰는 서비스명", example = "채소집",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @Size(max = 255, message = "서비스명은 255자 이하로 입력해 주세요")
    String serviceName) {

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

  /**
   * 온보딩 없으면 서비스명이 필수다.
   */
  @JsonIgnore
  @AssertTrue(message = "온보딩이 없으면 서비스명을 입력해 주세요")
  public boolean isServiceNameRequiredWhenNoOnboarding() {
    if (onboardingId != null) {
      return true;
    }
    return serviceName != null && !serviceName.isBlank();
  }
}
