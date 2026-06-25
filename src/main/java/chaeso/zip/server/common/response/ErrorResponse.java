package chaeso.zip.server.common.response;

import chaeso.zip.server.common.exception.ErrorCode;
import java.util.List;
import lombok.Getter;

/**
 * 에러 응답 본문. {@link ApiResponse#fail(ErrorResponse)} 의 {@code error} 필드로 직렬화된다.
 */
@Getter
public class ErrorResponse {

  private final String code;
  private final String message;
  private final List<FieldError> fieldErrors;

  private ErrorResponse(String code, String message, List<FieldError> fieldErrors) {
    this.code = code;
    this.message = message;
    this.fieldErrors = fieldErrors;
  }

  public static ErrorResponse of(ErrorCode errorCode) {
    return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
  }

  public static ErrorResponse of(ErrorCode errorCode, String message) {
    return new ErrorResponse(errorCode.getCode(), message, List.of());
  }

  public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
    return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), fieldErrors);
  }

  /**
   * 검증 실패 시 필드별 상세 에러.
   */
  @Getter
  public static class FieldError {

    private final String field;
    private final String value;
    private final String reason;

    private FieldError(String field, String value, String reason) {
      this.field = field;
      this.value = value;
      this.reason = reason;
    }

    public static FieldError of(String field, String value, String reason) {
      return new FieldError(field, value, reason);
    }
  }
}
