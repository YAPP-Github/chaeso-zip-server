package chaeso.zip.server.channel.presentation;

import chaeso.zip.server.channel.application.ChannelService;
import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController implements ChannelApiDocs {

  private final ChannelService channelService;

  @Override
  @GetMapping
  public ApiResponse<PageResponse<ChannelListItemResponse>> getChannels(
      @RequestParam(required = false) String name,
      @PageableDefault(size = 12, sort = "name") @ParameterObject Pageable pageable) {
    return ApiResponse.success(PageResponse.from(channelService.getChannels(name, pageable)));
  }

  @Override
  @GetMapping("/{id}")
  public ApiResponse<ChannelDetailResponse> getChannel(@PathVariable UUID id) {
    return ApiResponse.success(channelService.getChannel(id));
  }
}
