package chaeso.zip.server.comparison.domain;

import chaeso.zip.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChannelComparisonErrorCode implements ErrorCode {

  COMPARISON_NOT_FOUND(HttpStatus.NOT_FOUND, "CMP-001", "존재하지 않는 채널 비교입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
