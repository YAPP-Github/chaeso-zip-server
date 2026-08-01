package chaeso.zip.server.channel.presentation.dto;

import chaeso.zip.server.common.exception.BusinessException;
import chaeso.zip.server.common.exception.CommonErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Schema(description = "채널 목록 조회 요청")
public record ChannelSearchRequest(
    @Schema(description = "채널명 검색어", example = "11번가")
    String name,

    @Schema(description = "페이지 번호(0-base)", example = "0")
    Integer page,

    @Schema(description = "페이지 크기", example = "12")
    Integer size) {

  private static final int DEFAULT_PAGE_SIZE = 12;

  private static final int MAX_PAGE_SIZE = 100;

  /**
   * page/size 를 모두 지정하지 않으면 전체 조회(unpaged), 하나라도 지정하면 페이지 조회로 처리한다.
   */
  public Pageable toPageable(Sort sort) {
    if (page == null && size == null) {
      return Pageable.unpaged(sort);
    }
    int pageNumber = page == null ? 0 : page;
    int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
    if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE,
          "page 는 0 이상, size 는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다");
    }
    return PageRequest.of(pageNumber, pageSize, sort);
  }
}
