package chaeso.zip.server.auth.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.application.dto.GoogleAuthResponse;
import chaeso.zip.server.auth.application.dto.LoginMethodsResponse;
import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.application.dto.UserResponse;
import chaeso.zip.server.auth.presentation.dto.GoogleAuthRequest;
import chaeso.zip.server.auth.presentation.dto.GoogleSignupRequest;
import chaeso.zip.server.auth.presentation.dto.LoginMethodsRequest;
import chaeso.zip.server.auth.presentation.dto.LoginRequest;
import chaeso.zip.server.auth.presentation.dto.RefreshTokenRequest;
import chaeso.zip.server.auth.presentation.dto.SendVerificationCodeRequest;
import chaeso.zip.server.auth.presentation.dto.SignupRequest;
import chaeso.zip.server.auth.presentation.dto.VerifyEmailCodeRequest;
import chaeso.zip.server.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

/** 회원가입/인증/세션 API 문서 정의. 구현은 {@link AuthController}. */
@Tag(name = "Auth", description = "회원가입/이메일 인증/세션 API")
public interface AuthApiDocs {

  String VALIDATION_ERROR_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "C-001",
          "message": "입력값이 올바르지 않습니다.",
          "fieldErrors": [
            {
              "field": "email",
              "value": "",
              "reason": "이메일을 입력해 주세요"
            }
          ]
        }
      }
      """;

  String EMAIL_ALREADY_EXISTS_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "AUTH-002",
          "message": "이미 사용 중인 이메일입니다.",
          "fieldErrors": []
        }
      }
      """;

  String VERIFICATION_CODE_SEND_COOLDOWN_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "AUTH-008",
          "message": "인증 코드는 잠시 후 다시 요청해 주세요.",
          "fieldErrors": []
        }
      }
      """;

  String EMAIL_ALREADY_USED_WITH_GOOGLE_EXAMPLE = """
      {
        "success": true,
        "data": null,
        "error": null,
        "code": "EMAIL_ALREADY_USED_WITH_GOOGLE"
      }
      """;

  @Operation(operationId = "sendSignupCode", summary = "회원가입 이메일 인증코드 발송",
      description = "가입할 이메일로 6자리 인증코드를 발송한다. "
          + "로컬로 이미 가입된 이메일: 409. "
          + "구글로만 가입된 이메일: 발송하지 않고 200 과 code: EMAIL_ALREADY_USED_WITH_GOOGLE. "
          + "그 외: 발송 후 200.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
      description = "발송 성공. 구글로만 가입된 이메일이면 발송 없이 안내 코드만 실린다",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "EMAIL_ALREADY_USED_WITH_GOOGLE",
              value = EMAIL_ALREADY_USED_WITH_GOOGLE_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "EMAIL_ALREADY_EXISTS", value = EMAIL_ALREADY_EXISTS_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "인증번호 재발송 쿨다운(AUTH-008)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VERIFICATION_CODE_SEND_COOLDOWN",
              value = VERIFICATION_CODE_SEND_COOLDOWN_EXAMPLE)))
  ApiResponse<Void> sendSignupCode(@Valid @RequestBody SendVerificationCodeRequest request);

  String VERIFICATION_CODE_INVALID_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "AUTH-007",
          "message": "인증 코드가 올바르지 않거나 만료되었습니다.",
          "fieldErrors": []
        }
      }
      """;

  @Operation(operationId = "verifySignupCode", summary = "회원가입 이메일 인증코드 확인",
      description = "발송된 코드를 검증하고 인증완료 상태로 전환한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "코드 불일치/만료 또는 형식 오류",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = {
              @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE),
              @ExampleObject(name = "VERIFICATION_CODE_INVALID", value = VERIFICATION_CODE_INVALID_EXAMPLE)
          }))
  ApiResponse<Void> verifySignupCode(@Valid @RequestBody VerifyEmailCodeRequest request);

  String EMAIL_NOT_VERIFIED_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "AUTH-006",
          "message": "이메일 인증이 필요합니다.",
          "fieldErrors": []
        }
      }
      """;

  @Operation(operationId = "signup", summary = "회원가입 최종 제출",
      description = "이메일 인증 완료 후 로컬 계정을 생성한다. 인증 미완료 시 400, 이메일 중복 시 409.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "가입 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 이메일 미인증",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = {
              @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE),
              @ExampleObject(name = "EMAIL_NOT_VERIFIED", value = EMAIL_NOT_VERIFIED_EXAMPLE)
          }))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "EMAIL_ALREADY_EXISTS", value = EMAIL_ALREADY_EXISTS_EXAMPLE)))
  ApiResponse<UserResponse> signup(@Valid @RequestBody SignupRequest request);

  String INVALID_CREDENTIALS_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "AUTH-003",
          "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
          "fieldErrors": []
        }
      }
      """;

  String ACCOUNT_REGISTERED_WITH_GOOGLE_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "AUTH-010",
          "message": "Google 계정으로 가입된 이메일입니다. Google 로그인을 이용해 주세요.",
          "fieldErrors": []
        }
      }
      """;

  @Operation(operationId = "login", summary = "로컬 로그인",
      description = "이메일/비밀번호로 로그인하고 access/refresh 토큰을 발급한다. 자격증명이 올바르지 않으면 401. "
          + "구글로만 가입되어 로컬 인증정보가 없는 이메일이면 AUTH-010 으로 별도 응답한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
      description = "이메일 또는 비밀번호 불일치(AUTH-003), 또는 구글로만 가입된 계정(AUTH-010)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = {
              @ExampleObject(name = "INVALID_CREDENTIALS", value = INVALID_CREDENTIALS_EXAMPLE),
              @ExampleObject(name = "ACCOUNT_REGISTERED_WITH_GOOGLE",
                  value = ACCOUNT_REGISTERED_WITH_GOOGLE_EXAMPLE)
          }))
  ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request);

  String LOGIN_METHODS_EXAMPLE = """
      {
        "success": true,
        "data": {
          "methods": ["LOCAL", "GOOGLE"]
        },
        "error": null
      }
      """;

  String LOGIN_METHODS_EMPTY_EXAMPLE = """
      {
        "success": true,
        "data": {
          "methods": []
        },
        "error": null
      }
      """;

  String LOGIN_METHOD_LOOKUP_COOLDOWN_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "AUTH-012",
          "message": "조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.",
          "fieldErrors": []
        }
      }
      """;

  @Operation(operationId = "loginMethods", summary = "로그인 수단 조회",
      description = "이메일로 사용 가능한 로그인 수단을 조회한다. 비밀번호 입력 전 화면 분기용. "
          + "methods [LOCAL]: 비밀번호 입력창. "
          + "[LOCAL, GOOGLE]: 비밀번호 입력창과 구글 버튼. "
          + "[GOOGLE]: 구글로 가입된 계정 안내. "
          + "[]: 미가입 -> 회원가입 화면.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = {
              @ExampleObject(name = "REGISTERED", value = LOGIN_METHODS_EXAMPLE),
              @ExampleObject(name = "NOT_REGISTERED", value = LOGIN_METHODS_EMPTY_EXAMPLE)
          }))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
      description = "IP 단위 조회 한도 초과(AUTH-012)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "LOGIN_METHOD_LOOKUP_COOLDOWN",
              value = LOGIN_METHOD_LOOKUP_COOLDOWN_EXAMPLE)))
  ApiResponse<LoginMethodsResponse> loginMethods(
      @Valid @RequestBody LoginMethodsRequest request,
      @Parameter(hidden = true) HttpServletRequest httpRequest);

  String INVALID_REFRESH_TOKEN_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "AUTH-004",
          "message": "유효하지 않은 refresh 토큰입니다.",
          "fieldErrors": []
        }
      }
      """;

  String REFRESH_TOKEN_REUSE_DETECTED_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "AUTH-005",
          "message": "재사용이 감지되어 해당 세션이 만료되었습니다. 다시 로그인하세요.",
          "fieldErrors": []
        }
      }
      """;

  @Operation(operationId = "refresh", summary = "토큰 재발급",
      description = "Refresh Token 을 회전시켜 새 access/refresh 토큰 쌍을 발급한다. "
          + "기존 Refresh Token 은 즉시 폐기된다. 이미 회전된 토큰을 다시 제출하면 재사용으로 보고 "
          + "해당 세션(family) 전체를 폐기하므로, 동시에 여러 번 호출하지 말고 한 번만 보낸다. "
          + "Access Token 이 만료된 상태에서 호출하므로 인증이 필요 없다. "
          + "refreshTokenExpiresIn 은 로그인 후 90일에 가까워질수록 짧아진다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
      description = "토큰 만료/변조/세션 없음(AUTH-004) 또는 재사용 탐지(AUTH-005)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = {
              @ExampleObject(name = "INVALID_REFRESH_TOKEN", value = INVALID_REFRESH_TOKEN_EXAMPLE),
              @ExampleObject(name = "REFRESH_TOKEN_REUSE_DETECTED",
                  value = REFRESH_TOKEN_REUSE_DETECTED_EXAMPLE)
          }))
  ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request);

  String GOOGLE_AUTH_FAILED_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "AUTH-009",
          "message": "Google 인증에 실패했습니다. 다시 시도해 주세요.",
          "fieldErrors": []
        }
      }
      """;

  String GOOGLE_LOGIN_EXAMPLE = """
      {
        "success": true,
        "data": {
          "status": "LOGIN",
          "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
          "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
          "accessTokenExpiresIn": 1800,
          "refreshTokenExpiresIn": 1209600
        },
        "error": null
      }
      """;

  String GOOGLE_LINK_REQUIRED_EXAMPLE = """
      {
        "success": true,
        "data": {
          "status": "LINK_REQUIRED",
          "linkRequired": true,
          "email": "user@chaeso.zip"
        },
        "error": null
      }
      """;

  String GOOGLE_SIGNUP_REQUIRED_EXAMPLE = """
      {
        "success": true,
        "data": {
          "status": "SIGNUP_REQUIRED",
          "signupRequired": true,
          "signupToken": "0pxJ3n1Q...",
          "prefill": {
            "email": "user@chaeso.zip",
            "suggestedNickname": "홍길동"
          }
        },
        "error": null
      }
      """;

  @Operation(operationId = "googleAuth", summary = "구글 인증 진입",
      description = "구글 idToken 을 검증하고 계정 상태에 따라 status 로 분기한다. "
          + "LOGIN: 토큰 발급. "
          + "LINK_REQUIRED: linkRequired 와 email 을 내려주며, 사용자 확인 후 POST /auth/google/link 호출. "
          + "SIGNUP_REQUIRED: signupRequired 와 일회성 signupToken, 프리필 값을 내려준다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
      description = "로그인 성공 / 연결 확인 필요 / 가입 필요",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = {
              @ExampleObject(name = "LOGIN", value = GOOGLE_LOGIN_EXAMPLE),
              @ExampleObject(name = "LINK_REQUIRED", value = GOOGLE_LINK_REQUIRED_EXAMPLE),
              @ExampleObject(name = "SIGNUP_REQUIRED", value = GOOGLE_SIGNUP_REQUIRED_EXAMPLE)
          }))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
      description = "idToken 만료/위조/aud 불일치 또는 미검증 이메일(AUTH-009)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "GOOGLE_AUTH_FAILED", value = GOOGLE_AUTH_FAILED_EXAMPLE)))
  ApiResponse<GoogleAuthResponse> googleAuth(@Valid @RequestBody GoogleAuthRequest request);

  String GOOGLE_ACCOUNT_LINKED = "GOOGLE_ACCOUNT_LINKED";

  String GOOGLE_ACCOUNT_LINKED_EXAMPLE = """
      {
        "success": true,
        "data": {
          "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
          "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
          "accessTokenExpiresIn": 1800,
          "refreshTokenExpiresIn": 1209600
        },
        "error": null,
        "code": "GOOGLE_ACCOUNT_LINKED"
      }
      """;

  @Operation(operationId = "linkGoogle", summary = "구글 계정 연결 확인",
      description = "linkRequired:true 응답을 받고 사용자가 '예'를 선택했을 때만 호출한다. "
          + "진입 때 보낸 idToken 을 재전송하면 재검증 후 같은 이메일의 로컬 계정에 구글 로그인을 "
          + "연결하고 토큰을 발급한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
      description = "연결 완료(code: GOOGLE_ACCOUNT_LINKED) 및 토큰 발급",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "GOOGLE_ACCOUNT_LINKED", value = GOOGLE_ACCOUNT_LINKED_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
      description = "idToken 만료/위조 또는 연결할 계정 없음(AUTH-009)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "GOOGLE_AUTH_FAILED", value = GOOGLE_AUTH_FAILED_EXAMPLE)))
  ApiResponse<TokenResponse> linkGoogle(@Valid @RequestBody GoogleAuthRequest request);

  String GOOGLE_SIGNUP_SESSION_INVALID_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "AUTH-011",
          "message": "가입 세션이 만료되었습니다. Google 로그인을 다시 시도해 주세요.",
          "fieldErrors": []
        }
      }
      """;

  String GOOGLE_SIGNUP_SUCCESS_EXAMPLE = """
      {
        "success": true,
        "data": {
          "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
          "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
          "accessTokenExpiresIn": 1800,
          "refreshTokenExpiresIn": 1209600
        },
        "error": null
      }
      """;

  @Operation(operationId = "signupGoogle", summary = "구글 최종 회원가입",
      description = "POST /auth/google 이 내려준 signupToken 과 추가 프로필(닉네임/회사명/직무/약관 동의)로 "
          + "신규 회원가입을 완료하고 토큰을 발급한다. "
          + "가입에 성공하면 signupToken 은 즉시 폐기되어 재사용 불가능.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "가입 성공, 토큰 발급",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "SIGNUP_SUCCESS", value = GOOGLE_SIGNUP_SUCCESS_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
      description = "입력값 검증 실패(C-001) 또는 signupToken 만료/무효(AUTH-011)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = {
              @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE),
              @ExampleObject(name = "GOOGLE_SIGNUP_SESSION_INVALID",
                  value = GOOGLE_SIGNUP_SESSION_INVALID_EXAMPLE)
          }))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
      description = "가입 처리 중 타인이 먼저 같은 이메일로 가입함(AUTH-002)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "EMAIL_ALREADY_EXISTS", value = EMAIL_ALREADY_EXISTS_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
      description = "구글 sub 가 이미 다른 활성 계정에 연결돼 있는 에러 상태(AUTH-009)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "GOOGLE_AUTH_FAILED", value = GOOGLE_AUTH_FAILED_EXAMPLE)))
  ApiResponse<TokenResponse> signupGoogle(@Valid @RequestBody GoogleSignupRequest request);

  @SecurityRequirement(name = "bearerAuth")
  @Operation(operationId = "logout", summary = "로그아웃",
      description = "제출한 Refresh Token 의 세션(family)을 폐기한다. 인증된 사용자만 호출할 수 있고, "
          + "토큰 소유자가 인증 사용자와 다르면 401. 이미 폐기된 세션이어도 200 을 반환한다(멱등).")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
      description = "미인증 요청(C-004) 또는 토큰 무효/소유자 불일치(AUTH-004)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "INVALID_REFRESH_TOKEN", value = INVALID_REFRESH_TOKEN_EXAMPLE)))
  ApiResponse<Void> logout(
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody RefreshTokenRequest request);
}
