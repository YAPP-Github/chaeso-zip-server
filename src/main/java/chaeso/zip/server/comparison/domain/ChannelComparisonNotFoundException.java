package chaeso.zip.server.comparison.domain;

import chaeso.zip.server.common.exception.BusinessException;
import java.util.UUID;

public class ChannelComparisonNotFoundException extends BusinessException {

  public ChannelComparisonNotFoundException(UUID comparisonId) {
    super(ChannelComparisonErrorCode.COMPARISON_NOT_FOUND,
        "존재하지 않는 채널 비교입니다. id=" + comparisonId);
  }
}
