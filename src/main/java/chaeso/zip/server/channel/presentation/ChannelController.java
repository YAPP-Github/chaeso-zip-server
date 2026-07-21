package chaeso.zip.server.channel.presentation;

import chaeso.zip.server.channel.application.ChannelService;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Channel", description = "채널 카탈로그 API")
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {

  private final ChannelService channelService;

  @Operation(operationId = "getChannels", summary = "채널 목록 조회",
      description = "전체 채널을 페이지 단위로 조회한다. (기본값: 페이지당 12개, 정렬 이름순)")
  @GetMapping
  public ApiResponse<PageResponse<ChannelListItemResponse>> getChannels(
      @PageableDefault(size = 12, sort = "name") Pageable pageable) {
    return ApiResponse.success(PageResponse.from(channelService.getChannels(pageable)));
  }
}
