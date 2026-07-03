package chaeso.zip.server.user.application;

import chaeso.zip.server.common.exception.BusinessException;
import chaeso.zip.server.common.mail.VerificationMailSender;
import chaeso.zip.server.common.security.EmailVerificationCodeStore;
import chaeso.zip.server.user.domain.UserErrorCode;
import chaeso.zip.server.user.domain.UserRepository;
import java.security.SecureRandom;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 회원가입 전 이메일 인증코드 발송과 확인 흐름을 담당한다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int CODE_LENGTH = 6;
    private static final int CODE_UPPER_BOUND = 1_000_000;

    private final UserRepository userRepository;
    private final EmailVerificationCodeStore verificationCodeStore;
    private final VerificationMailSender verificationMailSender;

    private final SecureRandom secureRandom = new SecureRandom();

    public void sendSignupVerificationCode(String email) {
        String normalized = normalizeEmail(email);
        if (userRepository.existsByEmailAndDeletedAtIsNull(normalized)) {
            throw new BusinessException(UserErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String code = generateCode();
        verificationCodeStore.saveCode(normalized, code);
        verificationMailSender.sendVerificationCode(normalized, code);
    }

    public void verifySignupCode(String email, String code) {
        String normalized = normalizeEmail(email);
        String storedCode = verificationCodeStore.findCode(normalized)
                .orElseThrow(() -> new BusinessException(UserErrorCode.VERIFICATION_CODE_INVALID));
        if (!storedCode.equals(code)) {
            throw new BusinessException(UserErrorCode.VERIFICATION_CODE_INVALID);
        }
        verificationCodeStore.markVerified(normalized);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateCode() {
        return String.format("%0" + CODE_LENGTH + "d", secureRandom.nextInt(CODE_UPPER_BOUND));
    }
}
