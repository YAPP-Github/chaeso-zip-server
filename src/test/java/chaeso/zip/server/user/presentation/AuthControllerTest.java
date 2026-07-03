package chaeso.zip.server.user.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.user.application.AuthService;
import chaeso.zip.server.user.application.dto.LoginCommand;
import chaeso.zip.server.user.application.dto.SignupCommand;
import chaeso.zip.server.user.application.dto.TokenResponse;
import chaeso.zip.server.user.application.dto.UserResponse;
import chaeso.zip.server.user.domain.EmploymentStatus;
import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.user.presentation.dto.LoginRequest;
import chaeso.zip.server.user.presentation.dto.RefreshTokenRequest;
import chaeso.zip.server.user.presentation.dto.SendVerificationCodeRequest;
import chaeso.zip.server.user.presentation.dto.SignupRequest;
import chaeso.zip.server.user.presentation.dto.VerifyEmailCodeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private AuthService authService;

  @Test
  @DisplayName("인증코드 발송 요청이 유효하면 200을 반환한다")
  void sendSignupCode_success() throws Exception {
    mockMvc.perform(post("/api/v1/auth/signup/email-code")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SendVerificationCodeRequest("user@chaeso.zip"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    then(authService).should().sendSignupVerificationCode("user@chaeso.zip");
  }

  @Test
  @DisplayName("인증코드 확인 요청이 유효하면 200을 반환한다")
  void verifySignupCode_success() throws Exception {
    mockMvc.perform(post("/api/v1/auth/signup/email-code/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new VerifyEmailCodeRequest("user@chaeso.zip", "123456"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    then(authService).should().verifySignupCode("user@chaeso.zip", "123456");
  }

  @Test
  @DisplayName("회원가입 성공 시 201과 공통 응답 포맷을 반환한다")
  void signup_success() throws Exception {
    given(authService.signup(any(SignupCommand.class)))
        .willReturn(new UserResponse(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), "user@chaeso.zip", "채소러버",
            EmploymentStatus.EMPLOYEE));

    mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SignupRequest(
                "user@chaeso.zip", "P@ssw0rd!", "채소러버", EmploymentStatus.EMPLOYEE, "채소컴퍼니", Occupation.DEVELOPMENT,
                true, "v1.0", false))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("user@chaeso.zip"));
  }

  @Test
  @DisplayName("필수 약관에 동의하지 않으면 400과 검증 에러를 반환한다")
  void signup_termsNotAgreed() throws Exception {
    mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SignupRequest(
                "user@chaeso.zip", "P@ssw0rd!", "닉", EmploymentStatus.EMPLOYEE, null, null, false, "v1.0", false))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("C-001"));
  }

  @Test
  @DisplayName("이메일 형식이 틀리면 400과 검증 에러를 반환한다")
  void signup_invalidEmail() throws Exception {
    mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SignupRequest(
                "not-an-email", "P@ssw0rd!", "닉", EmploymentStatus.EMPLOYEE, null, null, true, "v1.0", false))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("C-001"));
  }

  @Test
  @DisplayName("로그인 성공 시 토큰을 반환한다")
  void login_success() throws Exception {
    given(authService.login(any(LoginCommand.class)))
        .willReturn(new TokenResponse("access", "refresh"));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new LoginRequest("user@chaeso.zip", "P@ssw0rd!"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("access"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh"));
  }

  @Test
  @DisplayName("토큰 재발급 성공 시 200과 신규 토큰을 반환한다")
  void reissue_success() throws Exception {
    given(authService.reissue("old-refresh"))
        .willReturn(new TokenResponse("new-access", "new-refresh"));

    mockMvc.perform(post("/api/v1/auth/reissue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RefreshTokenRequest("old-refresh"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("new-access"))
        .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));

    then(authService).should().reissue("old-refresh");
  }

  @Test
  @DisplayName("로그아웃 성공 시 200을 반환한다")
  void logout_success() throws Exception {
    mockMvc.perform(post("/api/v1/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RefreshTokenRequest("some-refresh"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    then(authService).should().logout("some-refresh");
  }
}