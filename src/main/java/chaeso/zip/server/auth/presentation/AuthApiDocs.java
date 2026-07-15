package chaeso.zip.server.auth.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.application.dto.TokenResponse;
import chaeso.zip.server.auth.application.dto.UserResponse;
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
import io.swagger.v3.oas.annotations.tags.Tag;
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

  @Operation(operationId = "sendSignupCode", summary = "회원가입 이메일 인증코드 발송",
      description = "가입할 이메일로 6자리 인증코드를 발송한다. 이미 가입된 이메일이면 409.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발송 성공")
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

  @Operation(operationId = "login", summary = "로컬 로그인",
      description = "이메일/비밀번호로 로그인하고 access/refresh 토큰을 발급한다. 자격증명이 올바르지 않으면 401. "
          + "refreshTokenExpiresIn 은 고정값이 아니므로 응답값을 그대로 쓴다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치(AUTH-003)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "INVALID_CREDENTIALS", value = INVALID_CREDENTIALS_EXAMPLE)))
  ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request);

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
