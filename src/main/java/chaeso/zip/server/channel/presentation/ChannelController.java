package chaeso.zip.server.channel.presentation;

import chaeso.zip.server.channel.application.ChannelService;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController implements ChannelApiDocs {

  private final ChannelService channelService;

  @Override
  @GetMapping
  public ApiResponse<PageResponse<ChannelListItemResponse>> getChannels(
      @PageableDefault(size = 12, sort = "name") Pageable pageable) {
    return ApiResponse.success(PageResponse.from(channelService.getChannels(pageable)));
  }
}
