package chaeso.zip.server.user.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.user.application.dto.UserProfileResponse;
import chaeso.zip.server.user.application.dto.WithdrawalResponse;
import chaeso.zip.server.user.presentation.dto.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "User", description = "내 정보 API")
@SecurityRequirement(name = "bearerAuth")
public interface UserApiDocs {

  String PROFILE_EXAMPLE = """
      {
        "success": true,
        "data": {
          "nickname": "채소러버",
          "email": "user@example.com",
          "companyName": "채소집",
          "occupation": "MARKETING"
        },
        "error": null,
        "code": null
      }
      """;

  String VALIDATION_ERROR_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "C-001",
          "message": "입력값이 올바르지 않습니다.",
          "fieldErrors": [
            {
              "field": "companyName",
              "value": "",
              "reason": "공백일 수 없습니다"
            }
          ]
        },
        "code": null
      }
      """;

  String USER_NOT_FOUND_EXAMPLE = """
      {
        "success": false,
        "data": null,
        "error": {
          "code": "USER-001",
          "message": "존재하지 않는 회원입니다. id=3f2504e0-4f89-11d3-9a0c-0305e82c3301",
          "fieldErrors": []
        },
        "code": null
      }
      """;

  String WITHDRAWAL_EXAMPLE = """
      {
        "success": true,
        "data": {
          "withdrawnAt": "2026-08-20T03:00:00Z"
        },
        "error": null,
        "code": null
      }
      """;

  @Operation(operationId = "getMyProfile", summary = "내 정보 조회",
      description = "닉네임, 이메일, 회사, 직무를 반환한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
      useReturnTypeSchema = true,
      content = @Content(examples = @ExampleObject(name = "PROFILE", value = PROFILE_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 회원(USER-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "USER_NOT_FOUND", value = USER_NOT_FOUND_EXAMPLE)))
  ApiResponse<UserProfileResponse> getMyProfile(
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);

  @Operation(operationId = "updateMyProfile", summary = "내 정보 수정",
      description = "회사와 직무만 수정한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공",
      useReturnTypeSchema = true,
      content = @Content(examples = @ExampleObject(name = "PROFILE", value = PROFILE_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
      description = "입력값 검증 실패(C-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "VALIDATION_ERROR", value = VALIDATION_ERROR_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 회원(USER-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "USER_NOT_FOUND", value = USER_NOT_FOUND_EXAMPLE)))
  ApiResponse<UserProfileResponse> updateMyProfile(
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody UpdateProfileRequest request);

  @Operation(operationId = "withdraw", summary = "회원 탈퇴",
      description = "회원 계정을 즉시 비활성화하고 탈퇴 시각을 반환한다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공",
      useReturnTypeSchema = true,
      content = @Content(examples = @ExampleObject(name = "WITHDRAWAL", value = WITHDRAWAL_EXAMPLE)))
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
      description = "존재하지 않는 회원(USER-001)",
      content = @Content(schema = @Schema(implementation = ApiResponse.class),
          examples = @ExampleObject(name = "USER_NOT_FOUND", value = USER_NOT_FOUND_EXAMPLE)))
  ApiResponse<WithdrawalResponse> withdraw(
      @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal);
}
