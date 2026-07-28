package chaeso.zip.server.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모든 API 의 공통 응답 포맷.
 *
 * <pre>
 * {
 *   "success": true,
 *   "data": { ... }
 * }
 * </pre>
 *
 * @param <T> 응답 본문 타입
 */
@Getter
@JsonInclude(Include.NON_NULL)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

  @Schema(description = "요청 성공 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
  private final boolean success;

  @Schema(description = "성공 시 응답 본문. 실패 시 null", nullable = true)
  private final T data;

  @Schema(description = "실패 시 에러 정보. 성공 시 null", nullable = true)
  private final ErrorResponse error;

  @Schema(description = "성공 안내 코드. 안내할 것이 없으면 응답에서 생략된다", example = "GOOGLE_ACCOUNT_LINKED",
      nullable = true)
  private final String code;

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null, null);
  }

  public static <T> ApiResponse<T> success(T data, String code) {
    return new ApiResponse<>(true, data, null, code);
  }

  public static ApiResponse<Void> success() {
    return new ApiResponse<>(true, null, null, null);
  }

  public static ApiResponse<Void> fail(ErrorResponse error) {
    return new ApiResponse<>(false, null, error, null);
  }
}
