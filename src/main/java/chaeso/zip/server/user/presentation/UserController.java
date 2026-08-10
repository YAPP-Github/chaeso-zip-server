package chaeso.zip.server.user.presentation;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.user.application.UserService;
import chaeso.zip.server.user.application.dto.UserProfileResponse;
import chaeso.zip.server.user.application.dto.WithdrawalResponse;
import chaeso.zip.server.user.presentation.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보 REST API.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController implements UserApiDocs {

  private final UserService userService;

  @Override
  @GetMapping
  public ApiResponse<UserProfileResponse> getMyProfile(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.success(userService.getMyProfile(principal.userId()));
  }

  @Override
  @PatchMapping
  public ApiResponse<UserProfileResponse> updateMyProfile(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody UpdateProfileRequest request) {
    return ApiResponse.success(
        userService.updateMyProfile(principal.userId(), request.toCommand()));
  }

  @Override
  @DeleteMapping
  public ApiResponse<WithdrawalResponse> withdraw(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.success(
        userService.withdraw(principal.userId(), principal.sessionVersion()));
  }
}
