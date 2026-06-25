package chaeso.zip.server.common.exception;

/**
 * 요청한 엔티티가 존재하지 않을 때 발생하는 공통 예외.
 */
public class EntityNotFoundException extends BusinessException {

  public EntityNotFoundException() {
    super(CommonErrorCode.RESOURCE_NOT_FOUND);
  }

  public EntityNotFoundException(String message) {
    super(CommonErrorCode.RESOURCE_NOT_FOUND, message);
  }
}
