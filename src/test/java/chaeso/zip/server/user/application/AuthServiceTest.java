package chaeso.zip.server.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import chaeso.zip.server.common.exception.BusinessException;
import chaeso.zip.server.common.mail.VerificationMailSender;
import chaeso.zip.server.common.security.EmailVerificationCodeStore;
import chaeso.zip.server.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationCodeStore verificationCodeStore;
    @Mock private VerificationMailSender verificationMailSender;

    @InjectMocks private AuthService authService;

    @Test
    @DisplayName("가입되지 않은 이메일이면 6자리 코드를 생성해 저장하고 메일로 발송한다")
    void sendSignupVerificationCode_success() {
        given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(false);

        authService.sendSignupVerificationCode("user@chaeso.zip");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        then(verificationCodeStore).should().saveCode(eq("user@chaeso.zip"), codeCaptor.capture());
        then(verificationMailSender).should()
                .sendVerificationCode(eq("user@chaeso.zip"), eq(codeCaptor.getValue()));
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 코드를 발송하지 않고 예외가 발생한다")
    void sendSignupVerificationCode_alreadyRegistered() {
        given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(true);

        assertThatThrownBy(() -> authService.sendSignupVerificationCode("user@chaeso.zip"))
                .isInstanceOf(BusinessException.class);
        then(verificationMailSender).should(never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("이메일 인증코드 발송 시 이메일을 정규화해 조회/저장/발송에 사용한다")
    void sendSignupVerificationCode_normalizesEmail() {
        given(userRepository.existsByEmailAndDeletedAtIsNull("user@chaeso.zip")).willReturn(false);

        authService.sendSignupVerificationCode("  User@Chaeso.Zip  ");

        then(userRepository).should().existsByEmailAndDeletedAtIsNull("user@chaeso.zip");
        then(verificationCodeStore).should().saveCode(eq("user@chaeso.zip"), any());
        then(verificationMailSender).should().sendVerificationCode(eq("user@chaeso.zip"), any());
    }

    @Test
    @DisplayName("저장된 코드와 일치하면 인증완료 처리한다")
    void verifySignupCode_success() {
        given(verificationCodeStore.findCode("user@chaeso.zip")).willReturn(Optional.of("123456"));

        authService.verifySignupCode("user@chaeso.zip", "123456");

        then(verificationCodeStore).should().markVerified("user@chaeso.zip");
    }

    @Test
    @DisplayName("코드가 일치하지 않으면 예외가 발생한다")
    void verifySignupCode_mismatch() {
        given(verificationCodeStore.findCode("user@chaeso.zip")).willReturn(Optional.of("123456"));

        assertThatThrownBy(() -> authService.verifySignupCode("user@chaeso.zip", "000000"))
                .isInstanceOf(BusinessException.class);
        then(verificationCodeStore).should(never()).markVerified(any());
    }

    @Test
    @DisplayName("이메일 인증코드 확인 시 이메일을 정규화해 저장된 코드를 조회한다")
    void verifySignupCode_normalizesEmail() {
        given(verificationCodeStore.findCode("user@chaeso.zip")).willReturn(Optional.of("123456"));

        authService.verifySignupCode("  User@Chaeso.Zip  ", "123456");

        then(verificationCodeStore).should().findCode("user@chaeso.zip");
        then(verificationCodeStore).should().markVerified("user@chaeso.zip");
    }

    @Test
    @DisplayName("저장된 코드가 없으면(만료) 예외가 발생한다")
    void verifySignupCode_expired() {
        given(verificationCodeStore.findCode("user@chaeso.zip")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifySignupCode("user@chaeso.zip", "123456"))
                .isInstanceOf(BusinessException.class);
    }
}
