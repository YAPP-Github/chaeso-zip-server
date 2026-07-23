package chaeso.zip.server.channel.presentation;

import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ChannelListItemResponse;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;

@Tag(name = "Channel", description = "채널 카탈로그 API")
public interface ChannelApiDocs {

  @Operation(operationId = "getChannels", summary = "채널 목록 조회",
      description = """
          채널을 페이지 단위로 조회한다. \
          미지정 시 이름순 12개. name 지정 시 채널명으로 필터링""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
  ApiResponse<PageResponse<ChannelListItemResponse>> getChannels(
      @Parameter(description = "채널명 검색어", example = "11번가") String name,
      @ParameterObject Pageable pageable);

  @Operation(operationId = "getChannel", summary = "채널 상세 조회",
      description = """
          채널 단건을 상세 조회한다. \
          채널 정보와 함께 광고 상품 목록, 오디언스 규모 지표, 집행 사례를 반환한다. \
          상품이 없는 채널은 products 를 빈 배열로 반환한다.""")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 채널")
  ApiResponse<ChannelDetailResponse> getChannel(
      @Parameter(description = "채널 식별자", example = "550e8400-e29b-41d4-a716-446655440000")
      UUID id);
}
