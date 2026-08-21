package chaeso.zip.server.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import chaeso.zip.server.auth.infrastructure.verification.EmailVerificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

class VerificationMailSenderTest {

  @Test
  @DisplayName("인증 메일 발송은 비동기로 실행된다")
  void sendsVerificationCodeAsynchronously() throws NoSuchMethodException {
    assertThat(VerificationMailSender.class
            .getMethod("sendVerificationCode", String.class, String.class)
            .isAnnotationPresent(Async.class))
        .isTrue();
  }

  @Test
  @DisplayName("수신자/발신자/코드가 담긴 인증 메일을 발송한다")
  void sendsVerificationCode() throws Exception {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    MimeMessage mimeMessage = new MimeMessage((Session) null);
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    EmailVerificationProperties properties =
        new EmailVerificationProperties(
            "no-reply@chaeso-zip.com",
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            5,
            Duration.ofMinutes(1));
    VerificationMailSender sender = new VerificationMailSender(mailSender, properties);

    sender.sendVerificationCode("user@chaeso.zip", "123456");

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    MimeMessage message = captor.getValue();
    assertThat(message.getRecipients(MimeMessage.RecipientType.TO)[0].toString())
        .hasToString("user@chaeso.zip");
    InternetAddress from = (InternetAddress) message.getFrom()[0];
    assertThat(from.getAddress()).isEqualTo("no-reply@chaeso-zip.com");
    assertThat(from.getPersonal()).isEqualTo("채소.zip");
    assertThat(message.getContent().toString())
        .contains("123456")
        .contains("5분");
  }

  @Test
  @DisplayName("잘못된 수신자 주소면 메일 템플릿 예외로 변환한다")
  void throwsMailTemplateExceptionWhenRecipientIsInvalid() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    MimeMessage mimeMessage = new MimeMessage((Session) null);
    when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    EmailVerificationProperties properties =
        new EmailVerificationProperties(
            "no-reply@chaeso-zip.com",
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            5,
            Duration.ofMinutes(1));
    VerificationMailSender sender = new VerificationMailSender(mailSender, properties);

    assertThatThrownBy(() -> sender.sendVerificationCode("invalid-address", "123456"))
        .isInstanceOf(MailTemplateException.class)
        .hasMessage("인증 이메일을 구성할 수 없습니다")
        .hasCauseInstanceOf(MessagingException.class);
    verify(mailSender, never()).send(any(MimeMessage.class));
  }
}
