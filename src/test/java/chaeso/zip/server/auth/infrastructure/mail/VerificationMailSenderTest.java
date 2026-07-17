package chaeso.zip.server.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import chaeso.zip.server.auth.infrastructure.verification.EmailVerificationProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class VerificationMailSenderTest {

  @Test
  @DisplayName("수신자/발신자/코드가 담긴 인증 메일을 발송한다")
  void sendsVerificationCode() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    EmailVerificationProperties properties =
        new EmailVerificationProperties(
            "no-reply@chaeso.zip", Duration.ofMinutes(5), Duration.ofMinutes(30), 5, Duration.ofMinutes(1));
    VerificationMailSender sender = new VerificationMailSender(mailSender, properties);

    sender.sendVerificationCode("user@chaeso.zip", "123456");

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    SimpleMailMessage message = captor.getValue();
    assertThat(message.getTo()).containsExactly("user@chaeso.zip");
    assertThat(message.getFrom()).isEqualTo("no-reply@chaeso.zip");
    assertThat(message.getText())
        .contains("123456")
        .contains("5분");
  }
}
