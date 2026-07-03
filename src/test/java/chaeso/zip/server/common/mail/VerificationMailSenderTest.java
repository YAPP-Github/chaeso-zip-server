package chaeso.zip.server.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import chaeso.zip.server.common.security.EmailVerificationProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class VerificationMailSenderTest {

    @Mock private JavaMailSender mailSender;

    @Test
    @DisplayName("인증코드 메일을 발송하면 발신자/수신자/제목/본문이 올바르게 구성된다")
    void sendVerificationCode_buildsExpectedMessage() {
        VerificationMailSender verificationMailSender = new VerificationMailSender(
                mailSender,
                new EmailVerificationProperties("no-reply@chaeso.zip", Duration.ofMinutes(7), Duration.ofMinutes(30)));

        verificationMailSender.sendVerificationCode("user@chaeso.zip", "123456");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        then(mailSender).should().send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@chaeso.zip");
        assertThat(message.getTo()).containsExactly("user@chaeso.zip");
        assertThat(message.getSubject()).isEqualTo("[채소.zip] 이메일 인증 코드");
        assertThat(message.getText()).contains("123456").contains("7분");
    }
}
