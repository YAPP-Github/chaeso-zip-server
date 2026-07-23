package chaeso.zip.server.channel.domain;

import chaeso.zip.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChannelErrorCode implements ErrorCode {

  CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "CH-001", "존재하지 않는 채널입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
