package chaeso.zip.server.common.response;

import chaeso.zip.server.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;

/**
 * 에러 응답 본문. {@link ApiResponse#fail(ErrorResponse)} 의 {@code error} 필드로 직렬화된다.
 */
@Schema(description = "에러 응답 본문")
@Getter
public class ErrorResponse {

  @Schema(description = "에러 코드. <도메인 약어>-<일련번호> 형식", example = "C-001",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private final String code;

  @Schema(description = "에러 메시지", example = "입력값이 올바르지 않습니다.",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private final String message;

  @Schema(description = "검증 실패 시 필드별 상세 에러. 검증 외 에러는 빈 배열",
      requiredMode = Schema.RequiredMode.REQUIRED)
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
  @Schema(description = "검증 실패 필드 상세")
  @Getter
  public static class FieldError {

    @Schema(description = "검증 실패한 필드명", example = "name",
        requiredMode = Schema.RequiredMode.REQUIRED)
    private final String field;

    @Schema(description = "거부된 입력값. 값이 없으면 빈 문자열", example = "",
        requiredMode = Schema.RequiredMode.REQUIRED)
    private final String value;

    @Schema(description = "실패 사유", example = "이름은 필수입니다.",
        requiredMode = Schema.RequiredMode.REQUIRED)
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
