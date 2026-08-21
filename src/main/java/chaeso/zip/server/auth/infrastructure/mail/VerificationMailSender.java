package chaeso.zip.server.auth.infrastructure.mail;

import chaeso.zip.server.auth.infrastructure.verification.EmailVerificationProperties;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 회원가입 이메일 인증코드 메일을 구성하고 발송한다. */
@Component
@RequiredArgsConstructor
public class VerificationMailSender {

  private static final String SUBJECT = "[채소.zip] 회원가입을 위한 이메일 인증";
  private static final String TEMPLATE = "templates/email/signup-verification.html";

  private final JavaMailSender mailSender;
  private final EmailVerificationProperties properties;

  @Async
  public CompletableFuture<Void> sendVerificationCode(String to, String code) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
      helper.setValidateAddresses(true);
      helper.setFrom(properties.from(), "채소.zip");
      helper.setTo(to);
      helper.setSubject(SUBJECT);
      helper.setText(render(code), true);
      mailSender.send(message);
      return CompletableFuture.completedFuture(null);
    } catch (MessagingException | UnsupportedEncodingException exception) {
      throw new MailTemplateException("인증 이메일을 구성할 수 없습니다", exception);
    }
  }

  private String render(String code) {
    try {
      return new ClassPathResource(TEMPLATE).getContentAsString(StandardCharsets.UTF_8)
          .replace("{{code}}", code)
          .replace("{{ttlMinutes}}", String.valueOf(properties.codeTtl().toMinutes()));
    } catch (java.io.IOException exception) {
      throw new MailTemplateException("인증 이메일 템플릿을 읽을 수 없습니다", exception);
    }
  }
}
