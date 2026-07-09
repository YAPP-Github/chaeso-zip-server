package chaeso.zip.server.auth.infrastructure.mail;

import chaeso.zip.server.auth.infrastructure.verification.EmailVerificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 회원가입 이메일 인증코드 메일을 구성하고 발송한다. */
@Component
@RequiredArgsConstructor
public class VerificationMailSender {

  private static final String SUBJECT = "[채소.zip] 이메일 인증 코드";

  private final JavaMailSender mailSender;
  private final EmailVerificationProperties properties;

  public void sendVerificationCode(String to, String code) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(properties.from());
    message.setTo(to);
    message.setSubject(SUBJECT);
    message.setText("인증 코드: " + code + " (" + properties.codeTtl().toMinutes() + "분 이내 입력해주세요)");
    mailSender.send(message);
  }
}
