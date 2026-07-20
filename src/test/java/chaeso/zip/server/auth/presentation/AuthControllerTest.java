package chaeso.zip.server.auth.presentation;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.auth.application.AuthService;
import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.application.dto.GoogleAuthResponse;
import chaeso.zip.server.auth.application.dto.GoogleSignupCommand;
import chaeso.zip.server.auth.application.dto.LoginCommand;
import chaeso.zip.server.auth.application.dto.LoginMethodsResponse;
import chaeso.zip.server.auth.application.dto.SignupCommand;
import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.application.dto.UserResponse;
import chaeso.zip.server.auth.domain.AuthBusinessException;
import chaeso.zip.server.auth.domain.AuthErrorCode;
import chaeso.zip.server.auth.domain.AuthProvider;
import chaeso.zip.server.auth.presentation.dto.GoogleAuthRequest;
import chaeso.zip.server.auth.presentation.dto.GoogleSignupRequest;
import chaeso.zip.server.auth.presentation.dto.LoginMethodsRequest;
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
  @DisplayName("구글로만 가입된 이메일이면 200과 EMAIL_ALREADY_USED_WITH_GOOGLE 안내 코드를 반환한다")
  void sendSignupCode_googleOnly() throws Exception {
    given(authService.sendSignupVerificationCode("user@chaeso.zip"))
        .willReturn("EMAIL_ALREADY_USED_WITH_GOOGLE");

    mockMvc.perform(post("/api/v1/auth/signup/email-code")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new SendVerificationCodeRequest("user@chaeso.zip"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED_WITH_GOOGLE"));
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
  @DisplayName("구글로만 가입된 계정으로 로그인하면 401과 AUTH-010을 반환한다")
  void login_accountRegisteredWithGoogle() throws Exception {
    given(authService.login(any(LoginCommand.class)))
        .willThrow(new AuthBusinessException(AuthErrorCode.ACCOUNT_REGISTERED_WITH_GOOGLE));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validLoginRequest())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH-010"));
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

  @Test
  @DisplayName("구글 로그인 분기는 토큰만 내려주고 분기 플래그를 싣지 않는다")
  void googleAuth_login() throws Exception {
    given(authService.googleAuth("id-token")).willReturn(
        GoogleAuthResponse.login(new TokenResponse("access", "refresh", 1800L, 1209600L)));

    mockMvc.perform(post("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new GoogleAuthRequest("id-token"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("LOGIN"))
        .andExpect(jsonPath("$.data.accessToken").value("access"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh"))
        .andExpect(jsonPath("$.data.linkRequired").doesNotExist())
        .andExpect(jsonPath("$.data.signupRequired").doesNotExist())
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  @DisplayName("연결 확인 분기는 linkRequired 와 email 만 내려주고 토큰을 싣지 않는다")
  void googleAuth_linkRequired() throws Exception {
    given(authService.googleAuth("id-token"))
        .willReturn(GoogleAuthResponse.linkRequired("user@chaeso.zip"));

    mockMvc.perform(post("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new GoogleAuthRequest("id-token"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("LINK_REQUIRED"))
        .andExpect(jsonPath("$.data.linkRequired").value(true))
        .andExpect(jsonPath("$.data.email").value("user@chaeso.zip"))
        .andExpect(jsonPath("$.data.accessToken").doesNotExist())
        .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
  }

  @Test
  @DisplayName("가입 분기는 signupToken 과 프리필을 내려주고 토큰을 싣지 않는다")
  void googleAuth_signupRequired() throws Exception {
    given(authService.googleAuth("id-token")).willReturn(
        GoogleAuthResponse.signupRequired("signup-ticket", "user@chaeso.zip", "홍길동"));

    mockMvc.perform(post("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new GoogleAuthRequest("id-token"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
        .andExpect(jsonPath("$.data.signupRequired").value(true))
        .andExpect(jsonPath("$.data.signupToken").value("signup-ticket"))
        .andExpect(jsonPath("$.data.prefill.email").value("user@chaeso.zip"))
        .andExpect(jsonPath("$.data.prefill.suggestedNickname").value("홍길동"))
        .andExpect(jsonPath("$.data.accessToken").doesNotExist());
  }

  @Test
  @DisplayName("idToken 이 공백이면 C-001과 필드 에러를 반환한다")
  void googleAuth_blankIdToken() throws Exception {
    mockMvc.perform(post("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new GoogleAuthRequest(""))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[*].field").value(hasItem("idToken")));
  }

  @Test
  @DisplayName("idToken 검증에 실패하면 401 AUTH-009를 반환한다")
  void googleAuth_invalidIdToken() throws Exception {
    willThrow(new AuthBusinessException(AuthErrorCode.GOOGLE_AUTH_FAILED))
        .given(authService).googleAuth("bad-token");

    mockMvc.perform(post("/api/v1/auth/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new GoogleAuthRequest("bad-token"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH-009"));
  }

  @Test
  @DisplayName("연결이 끝나면 토큰과 함께 GOOGLE_ACCOUNT_LINKED 안내 코드를 내려준다")
  void linkGoogle_success() throws Exception {
    given(authService.linkGoogle("id-token"))
        .willReturn(new TokenResponse("access", "refresh", 1800L, 1209600L));

    mockMvc.perform(post("/api/v1/auth/google/link")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new GoogleAuthRequest("id-token"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_LINKED"))
        .andExpect(jsonPath("$.data.accessToken").value("access"))
        .andExpect(jsonPath("$.error").doesNotExist());
  }

  private static GoogleSignupRequest validGoogleSignupRequest() {
    return new GoogleSignupRequest("signup-ticket", "채소러버", "채소컴퍼니", Occupation.DEVELOPMENT,
        true, false);
  }

  @Test
  @DisplayName("구글 최종 회원가입이 성공하면 200과 토큰을 반환한다")
  void signupGoogle_success() throws Exception {
    given(authService.signupGoogle(any(GoogleSignupCommand.class)))
        .willReturn(new TokenResponse("access", "refresh", 1800L, 1209600L));

    mockMvc.perform(post("/api/v1/auth/signup/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validGoogleSignupRequest())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh"));
  }

  @Test
  @DisplayName("signupToken이 공백이면 C-001과 필드 에러를 반환한다")
  void signupGoogle_blankSignupToken() throws Exception {
    GoogleSignupRequest request = new GoogleSignupRequest("", "채소러버", "채소컴퍼니",
        Occupation.DEVELOPMENT, true, false);

    mockMvc.perform(post("/api/v1/auth/signup/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[*].field").value(hasItem("signupToken")));
  }

  @Test
  @DisplayName("필수 약관에 동의하지 않으면 C-001과 필드 에러를 반환한다")
  void signupGoogle_termsNotAgreed() throws Exception {
    GoogleSignupRequest request = new GoogleSignupRequest("signup-ticket", "채소러버", "채소컴퍼니",
        Occupation.DEVELOPMENT, false, false);

    mockMvc.perform(post("/api/v1/auth/signup/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[*].field").value(hasItem("termsAgreed")));
  }

  @Test
  @DisplayName("가입 티켓이 만료됐으면 400 AUTH-011을 반환한다")
  void signupGoogle_expiredTicket() throws Exception {
    willThrow(new AuthBusinessException(AuthErrorCode.GOOGLE_SIGNUP_SESSION_INVALID))
        .given(authService).signupGoogle(any(GoogleSignupCommand.class));

    mockMvc.perform(post("/api/v1/auth/signup/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validGoogleSignupRequest())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("AUTH-011"));
  }

  @Test
  @DisplayName("가입 처리 중 이메일이 선점됐으면 409 AUTH-002를 반환한다")
  void signupGoogle_emailAlreadyExists() throws Exception {
    willThrow(new AuthBusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS))
        .given(authService).signupGoogle(any(GoogleSignupCommand.class));

    mockMvc.perform(post("/api/v1/auth/signup/google")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validGoogleSignupRequest())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("AUTH-002"));
  }

  @Test
  @DisplayName("이메일로 로그인 수단을 조회하면 methods 배열을 돌려준다")
  void loginMethods_returnsMethods() throws Exception {
    given(authService.findLoginMethods(anyString(), anyString()))
        .willReturn(new LoginMethodsResponse(List.of(AuthProvider.LOCAL, AuthProvider.GOOGLE)));

    mockMvc.perform(post("/api/v1/auth/login/methods")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new LoginMethodsRequest("user@chaeso.zip"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.methods[0]").value("LOCAL"))
        .andExpect(jsonPath("$.data.methods[1]").value("GOOGLE"));

    then(authService).should().findLoginMethods(eq("user@chaeso.zip"), anyString());
  }

  @Test
  @DisplayName("이메일 형식이 아니면 400 을 돌려준다")
  void loginMethods_invalidEmail_returns400() throws Exception {
    mockMvc.perform(post("/api/v1/auth/login/methods")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new LoginMethodsRequest("not-an-email"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("C-001"));
  }

  @Test
  @DisplayName("조회 한도를 넘으면 429 AUTH-012 를 돌려준다")
  void loginMethods_rateLimited_returns429() throws Exception {
    willThrow(new AuthBusinessException(AuthErrorCode.LOGIN_METHOD_LOOKUP_COOLDOWN))
        .given(authService).findLoginMethods(anyString(), anyString());

    mockMvc.perform(post("/api/v1/auth/login/methods")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new LoginMethodsRequest("user@chaeso.zip"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value("AUTH-012"));
  }
}
