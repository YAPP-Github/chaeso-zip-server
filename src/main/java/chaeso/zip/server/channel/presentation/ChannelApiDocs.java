package chaeso.zip.server.channel.presentation;

import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;

@Tag(name = "Channel", description = "채널 카탈로그 API")
public interface ChannelApiDocs {

  @Operation(operationId = "getChannels", summary = "채널 목록 조회",
      description = "전체 채널을 페이지 단위로 조회한다. 인증 없이 접근 가능하며, 미지정 시 이름순 12개.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
  ApiResponse<PageResponse<ChannelListItemResponse>> getChannels(@ParameterObject Pageable pageable);
}
