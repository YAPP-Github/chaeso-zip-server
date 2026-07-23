package chaeso.zip.server.channel.domain;

import chaeso.zip.server.common.exception.BusinessException;
import java.util.UUID;

public class ChannelNotFoundException extends BusinessException {

  public ChannelNotFoundException(UUID id) {
    super(ChannelErrorCode.CHANNEL_NOT_FOUND, "존재하지 않는 채널입니다. id=" + id);
  }
}
