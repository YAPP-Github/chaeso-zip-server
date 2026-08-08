package chaeso.zip.server.simulation.presentation.dto;

import chaeso.zip.server.common.exception.BusinessException;
import chaeso.zip.server.common.exception.CommonErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@Schema(description = "저장된 시뮬레이션 목록 조회 요청")
public record SimulationPageRequest(
    @Schema(description = "페이지 번호(0-base)", example = "0")
    Integer page,

    @Schema(description = "페이지 크기", example = "5")
    Integer size) {

  private static final int DEFAULT_PAGE_SIZE = 5;

  private static final int MAX_PAGE_SIZE = 50;

  /**
   * 정렬은 최신순으로 고정이라 클라이언트가 바꿀 수 없고, 목록은 항상 페이지로 끊어 준다.
   */
  public Pageable toPageable() {
    int pageNumber = page == null ? 0 : page;
    int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
    if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE,
          "page 는 0 이상, size 는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다");
    }
    return PageRequest.of(pageNumber, pageSize);
  }
}
