package chaeso.zip.server.auth.infrastructure.mail;

/** 메일 템플릿을 읽거나 구성하지 못했을 때 발생하는 인프라 예외. */
public class MailTemplateException extends RuntimeException {

  public MailTemplateException(String message, Throwable cause) {
    super(message, cause);
  }
}
