package chaeso.zip.server.common.exception;

import chaeso.zip.server.common.ratelimit.RateLimitExceededException;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.ErrorResponse;
import chaeso.zip.server.common.response.ErrorResponse.FieldError;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 전역 예외 처리기. 모든 예외를 {@link ApiResponse} 포맷으로 변환한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  /** 검증 실패 응답에 값을 그대로 전달하면 안 되는 보호 필드 */
  private static final Set<String> SENSITIVE_FIELDS = Set.of("password", "rawPassword");

  /**
   * 비즈니스 예외 처리.
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
    ErrorCode errorCode = e.getErrorCode();
    log.warn("BusinessException: {} - {}", errorCode.getCode(), e.getMessage());
    return toResponse(errorCode, ErrorResponse.of(errorCode, e.getMessage()));
  }

  /**
   * 재시도 시점 안내가 필요한 비즈니스 예외 처리. Retry-After 헤더로 재시도 시점을 안내한다.
   */
  @ExceptionHandler({RateLimitExceededException.class, RetryAfterBusinessException.class})
  public ResponseEntity<ApiResponse<Void>> handleRetryAfterException(BusinessException e) {
    RetryAfterAware retryAfterAware = (RetryAfterAware) e;
    ErrorCode errorCode = e.getErrorCode();
    Duration retryAfter = retryAfterAware.getRetryAfter();
    log.warn("RetryAfterException: {} - retryAfter={}", errorCode.getCode(), retryAfter);
    return retryAfterResponse(errorCode, e.getMessage(), retryAfter);
  }

  /**
   * 처리되지 않은 모든 예외 처리. 예기치 못한 서버 오류로 간주한다.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
    log.error("Unhandled exception", e);
    ErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
    return toResponse(errorCode, ErrorResponse.of(errorCode));
  }

  /**
   * {@code @Valid} 검증 실패 처리.
   */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ErrorCode errorCode = CommonErrorCode.INVALID_INPUT_VALUE;
    ErrorResponse error = ErrorResponse.of(errorCode, toFieldErrors(ex.getBindingResult()));
    return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(error));
  }

  /**
   * 지원하지 않는 HTTP 메서드 처리.
   */
  @Override
  protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ErrorCode errorCode = CommonErrorCode.METHOD_NOT_ALLOWED;
    return ResponseEntity.status(errorCode.getHttpStatus())
        .body(ApiResponse.fail(ErrorResponse.of(errorCode)));
  }

  /**
   * 공통 오류 응답을 {@link ApiResponse} 형식으로 감싼다.
   */
  private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, ErrorResponse error) {
    return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.fail(error));
  }

  /**
   * 재시도 가능한 오류 응답에 Retry-After 헤더를 추가한다.
   */
  private ResponseEntity<ApiResponse<Void>> retryAfterResponse(
      ErrorCode errorCode, String message, Duration retryAfter) {
    return ResponseEntity.status(errorCode.getHttpStatus())
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds(retryAfter)))
        .body(ApiResponse.fail(ErrorResponse.of(errorCode, message)));
  }

  /**
   * Retry-After 헤더 값에 맞춰 대기 시간을 초 단위로 올림한다.
   */
  private long retryAfterSeconds(Duration retryAfter) {
    return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
  }

  private List<FieldError> toFieldErrors(BindingResult bindingResult) {
    return bindingResult.getFieldErrors().stream()
        .map(error -> FieldError.of(
            error.getField(),
            rejectedValue(error),
            error.getDefaultMessage()))
        .toList();
  }

  private String rejectedValue(org.springframework.validation.FieldError error) {
    if (SENSITIVE_FIELDS.contains(error.getField())) {
      return "";
    }
    Object rejectedValue = error.getRejectedValue();
    return rejectedValue == null ? "" : rejectedValue.toString();
  }
}
