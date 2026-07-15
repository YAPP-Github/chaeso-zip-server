package chaeso.zip.server.auth.presentation;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.auth.application.AuthService;
import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.application.dto.LoginCommand;
import chaeso.zip.server.auth.application.dto.SignupCommand;
import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.application.dto.UserResponse;
import chaeso.zip.server.auth.domain.AuthBusinessException;
import chaeso.zip.server.auth.domain.AuthErrorCode;
import chaeso.zip.server.auth.presentation.dto.LoginRequest;
import chaeso.zip.server.auth.presentation.dto.RefreshTokenRequest;
import chaeso.zip.server.auth.presentation.dto.SendVerificationCodeRequest;
import chaeso.zip.server.auth.presentation.dto.SignupRequest;
import chaeso.zip.server.auth.presentation.dto.VerifyEmailCodeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import chaeso.zip.server.user.domain.Occupation;

/**
 * 인증 표현 계층 슬라이스 테스트. 공통 응답과 검증 에러 포맷을 검증한다.
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AuthController.class)
class AuthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private AuthService authService;

  private static SignupRequest validSignupRequest() {
    return new SignupRequest(
        "user@chaeso.zip",
        "P@ssw0rd!",
        "채소러버",
        "채소컴퍼니",
        Occupation.DEVELOPMENT,
        true,
        false);
  }

  private static LoginRequest validLoginRequest() {
    return new LoginRequest("user@chaeso.zip", "P@ssw0rd!");
  }

  private static final UUID AUTHENTICATED_USER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private static void authenticateAs(UUID userId) {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(new UserPrincipal(userId), null, List.of()));
  }

  @Test
  @DisplayName("회원가입 요청이 성공하면 201과 회원 정보를 반환한다")
  void signup_success() throws Exception {
    given(authService.signup(any(SignupCommand.class)))
        .willReturn(new UserResponse(UUID.randomUUID(), "user@chaeso.zip", "채소러버"));

    mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validSignupRequest())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.email").value("user@chaeso.zip"));
  }

  @Test
  @DisplayName("비밀번호 복잡도 조건을 만족하지 않으면 400과 password 필드 에러를 반환한다")
  void signup_weakPassword() throws Exception {
    SignupRequest request = new SignupRequest(
        "user@chaeso.zip",
        "password123",
        "채소러버",
        "채소컴퍼니",
        Occupation.DEVELOPMENT,
        true,
        false);

    mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'password')]").exists());
  }

  @Test
  @DisplayName("이미 가입된 이메일이면 409와 AUTH-002를 반환한다")
  void signup_emailAlreadyExists() throws Exception {
    given(authService.signup(any(SignupCommand.class)))
        .willThrow(new AuthBusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS));

    mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validSignupRequest())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("AUTH-002"));
  }

  @Test
  @DisplayName("이메일 인증이 완료되지 않으면 400과 AUTH-006을 반환한다")
  void signup_emailNotVerified() throws Exception {
    given(authService.signup(any(SignupCommand.class)))
        .willThrow(new AuthBusinessException(AuthErrorCode.EMAIL_NOT_VERIFIED));

    mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validSignupRequest())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("AUTH-006"));
  }

  @Test
  @DisplayName("인증 코드 발송 요청이 성공하면 200과 공통 응답 포맷을 반환한다")
  void sendSignupCode_success() throws Exception {
    mockMvc.perform(post("/api/v1/auth/signup/email-code")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SendVerificationCodeRequest("user@chaeso.zip"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("인증 코드가 6자리 숫자 형식이 아니면 400과 code 필드 에러를 반환한다")
  void verifySignupCode_invalidFormat() throws Exception {
    mockMvc.perform(post("/api/v1/auth/signup/email-code/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new VerifyEmailCodeRequest("user@chaeso.zip", "12"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'code')]").exists());
  }

  @Test
  @DisplayName("이미 가입된 이메일로 인증 코드를 요청하면 409와 AUTH-002를 반환한다")
  void sendSignupCode_duplicate() throws Exception {
    willThrow(new AuthBusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS))
        .given(authService).sendSignupVerificationCode(anyString());

    mockMvc.perform(post("/api/v1/auth/signup/email-code")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SendVerificationCodeRequest("user@chaeso.zip"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("AUTH-002"));
  }

  @Test
  @DisplayName("로그인 요청이 성공하면 200과 토큰/만료 시간을 반환한다")
  void login_success() throws Exception {
    given(authService.login(any(LoginCommand.class)))
        .willReturn(new TokenResponse("ACCESS", "REFRESH", 1800L, 1209600L));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validLoginRequest())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("ACCESS"))
        .andExpect(jsonPath("$.data.refreshToken").value("REFRESH"))
        .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(1800))
        .andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(1209600));
  }

  @Test
  @DisplayName("이메일 형식이 올바르지 않으면 400과 email 필드 에러를 반환한다")
  void login_invalidEmail() throws Exception {
    LoginRequest request = new LoginRequest("not-an-email", "P@ssw0rd!");

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'email')]").exists());
  }

  @Test
  @DisplayName("비밀번호가 공백이면 400과 password 필드 에러를 반환하고 rejected value는 비운다")
  void login_blankPassword() throws Exception {
    LoginRequest request = new LoginRequest("user@chaeso.zip", " ");

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'password')]").exists())
        .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'password')].value").value(hasItem("")));
  }

  @Test
  @DisplayName("자격증명이 올바르지 않으면 401과 AUTH-003을 반환한다")
  void login_invalidCredentials() throws Exception {
    given(authService.login(any(LoginCommand.class)))
        .willThrow(new AuthBusinessException(AuthErrorCode.INVALID_CREDENTIALS));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validLoginRequest())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH-003"));
  }

  @Test
  @DisplayName("재발급 요청이 성공하면 200과 새 토큰 쌍을 반환한다")
  void refresh_success() throws Exception {
    given(authService.reissue("valid-refresh"))
        .willReturn(new TokenResponse("new-access", "new-refresh", 1800L, 1209600L));

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RefreshTokenRequest("valid-refresh"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("new-access"))
        .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
  }

  @Test
  @DisplayName("만료/변조된 토큰으로 재발급하면 401과 AUTH-004를 반환한다")
  void refresh_invalidToken() throws Exception {
    given(authService.reissue("bad-refresh"))
        .willThrow(new AuthBusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RefreshTokenRequest("bad-refresh"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH-004"));
  }

  @Test
  @DisplayName("재사용이 탐지되면 401과 AUTH-005를 반환한다")
  void refresh_reuseDetected() throws Exception {
    given(authService.reissue("replayed"))
        .willThrow(new AuthBusinessException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED));

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RefreshTokenRequest("replayed"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH-005"));
  }

  @Test
  @DisplayName("refreshToken이 비어 있으면 400과 refreshToken 필드 에러를 반환한다")
  void refresh_blankToken() throws Exception {
    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RefreshTokenRequest(""))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[*].field").value(hasItem("refreshToken")));
  }

  @Test
  @DisplayName("로그아웃이 성공하면 200을 반환하고 인증된 사용자 id로 세션을 폐기한다")
  void logout_success() throws Exception {
    authenticateAs(AUTHENTICATED_USER_ID);

    mockMvc.perform(post("/api/v1/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new RefreshTokenRequest("my-refresh"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    then(authService).should().logout(AUTHENTICATED_USER_ID, "my-refresh");
  }

}
