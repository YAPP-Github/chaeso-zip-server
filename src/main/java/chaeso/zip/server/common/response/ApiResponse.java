package chaeso.zip.server.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모든 API 의 공통 응답 포맷.
 *
 * <pre>
 * {
 *   "success": true,
 *   "data": { ... },
 *   "error": null
 * }
 * </pre>
 *
 * @param <T> 응답 본문 타입
 */
@Getter
@JsonInclude(Include.NON_NULL)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

  private final boolean success;
  private final T data;
  private final ErrorResponse error;

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static ApiResponse<Void> success() {
    return new ApiResponse<>(true, null, null);
  }

  public static ApiResponse<Void> fail(ErrorResponse error) {
    return new ApiResponse<>(false, null, error);
  }
}
