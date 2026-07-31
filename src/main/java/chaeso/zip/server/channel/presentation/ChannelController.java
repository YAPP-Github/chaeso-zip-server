package chaeso.zip.server.channel.presentation;

import chaeso.zip.server.channel.application.ChannelService;
import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.channel.presentation.dto.ChannelSearchRequest;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
      @ParameterObject ChannelSearchRequest request,
      @SortDefault(sort = "name") @ParameterObject Sort sort) {
    return ApiResponse.success(PageResponse.from(
        channelService.getChannels(request.name(), request.toPageable(sort))));
  }

  @Override
  @GetMapping("/{id}")
  public ApiResponse<ChannelDetailResponse> getChannel(@PathVariable UUID id) {
    return ApiResponse.success(channelService.getChannel(id));
  }
}
