package chaeso.zip.server.common.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지네이션 응답 공통 포맷
 */
@Schema(description = "페이지네이션 응답")
public record PageResponse<T>(
    @Schema(description = "현재 페이지 항목", requiredMode = Schema.RequiredMode.REQUIRED)
    List<T> content,
    @Schema(description = "현재 페이지 번호(0-base)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    int number,
    @Schema(description = "페이지 크기", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    int size,
    @Schema(description = "전체 항목 수", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
    long totalElements,
    @Schema(description = "전체 페이지 수", example = "9", requiredMode = Schema.RequiredMode.REQUIRED)
    int totalPages,
    @Schema(description = "첫 페이지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean first,
    @Schema(description = "마지막 페이지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean last) {

  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }
}
