package chaeso.zip.server.sample.domain;

import chaeso.zip.server.common.exception.BusinessException;

/**
 * 샘플을 찾을 수 없을 때 발생하는 도메인 예외.
 */
public class SampleNotFoundException extends BusinessException {

  public SampleNotFoundException(Long id) {
    super(SampleErrorCode.SAMPLE_NOT_FOUND, "샘플을 찾을 수 없습니다. id=" + id);
  }
}
