package chaeso.zip.server.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 전역 공통 에러 코드. 특정 도메인에 속하지 않는 에러를 정의한다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

  INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C-001", "입력값이 올바르지 않습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C-002", "지원하지 않는 HTTP 메서드입니다."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C-003", "요청한 리소스를 찾을 수 없습니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C-999", "서버 내부 오류가 발생했습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
