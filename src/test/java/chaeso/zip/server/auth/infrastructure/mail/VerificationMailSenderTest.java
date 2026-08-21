package chaeso.zip.server.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import chaeso.zip.server.auth.infrastructure.verification.EmailVerificationProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
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
            "no-reply@chaeso.zip", Duration.ofMinutes(5), Duration.ofMinutes(30), 5, Duration.ofMinutes(1));
    VerificationMailSender sender = new VerificationMailSender(mailSender, properties);

    sender.sendVerificationCode("user@chaeso.zip", "123456");

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());
    MimeMessage message = captor.getValue();
    assertThat(message.getRecipients(MimeMessage.RecipientType.TO)[0].toString())
        .hasToString("user@chaeso.zip");
    assertThat(message.getFrom()[0].toString()).contains("no-reply@chaeso.zip");
    assertThat(message.getContent().toString())
        .contains("123456")
        .contains("5분");
  }
}
