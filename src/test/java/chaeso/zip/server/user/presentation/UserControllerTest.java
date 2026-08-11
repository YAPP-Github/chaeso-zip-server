package chaeso.zip.server.user.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.common.ratelimit.RateLimiter;
import chaeso.zip.server.support.security.WithUserPrincipal;
import chaeso.zip.server.user.application.UserService;
import chaeso.zip.server.user.application.dto.UpdateProfileCommand;
import chaeso.zip.server.user.application.dto.UserProfileResponse;
import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.user.domain.UserNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(UserController.class)
@WithUserPrincipal
class UserControllerTest {

  private static final UUID USER_ID = UUID.fromString(WithUserPrincipal.DEFAULT_USER_ID);

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private UserService userService;

  @MockitoBean
  private RateLimiter rateLimiter;

  @Nested
  @DisplayName("내 정보 조회")
  class GetMyProfile {

    @Test
    @DisplayName("닉네임/계정 이름/회사/직무를 반환한다")
    void returnsProfile() throws Exception {
      given(userService.getMyProfile(USER_ID)).willReturn(
          new UserProfileResponse("채소짱", "user@example.com", "채소집", Occupation.MARKETING));

      mockMvc.perform(get("/api/v1/users/me"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success").value(true))
          .andExpect(jsonPath("$.data.nickname").value("채소짱"))
          .andExpect(jsonPath("$.data.email").value("user@example.com"))
          .andExpect(jsonPath("$.data.companyName").value("채소집"))
          .andExpect(jsonPath("$.data.occupation").value("MARKETING"));
    }

    @Test
    @DisplayName("존재하지 않는 회원이면 404를 반환한다")
    void returns404WhenUserMissing() throws Exception {
      given(userService.getMyProfile(USER_ID)).willThrow(new UserNotFoundException(USER_ID));

      mockMvc.perform(get("/api/v1/users/me"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("USER-001"));
    }
  }

  @Nested
  @DisplayName("내 정보 수정")
  class UpdateMyProfile {

    @Test
    @DisplayName("요청이 성공하면 200 과 변경된 정보를 반환한다")
    void returnsUpdatedProfile() throws Exception {
      given(userService.updateMyProfile(eq(USER_ID), any(UpdateProfileCommand.class)))
          .willReturn(new UserProfileResponse("채소짱", "user@example.com", "새회사", Occupation.DATA));

      mockMvc.perform(patch("/api/v1/users/me")
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {"companyName": "새회사", "occupation": "DATA"}
                  """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.companyName").value("새회사"))
          .andExpect(jsonPath("$.data.occupation").value("DATA"));
    }

    @Test
    @DisplayName("회사명이 비어 있으면 400 을 반환한다")
    void rejectsBlankCompanyName() throws Exception {
      mockMvc.perform(patch("/api/v1/users/me")
              .contentType(MediaType.APPLICATION_JSON)
              .content("""
                  {"companyName": "", "occupation": "DATA"}
                  """))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("C-001"));
    }
  }
}
