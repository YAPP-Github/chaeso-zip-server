package chaeso.zip.server.sample.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.sample.application.SampleService;
import chaeso.zip.server.sample.application.dto.CreateSampleCommand;
import chaeso.zip.server.sample.application.dto.SampleResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 표현 계층 슬라이스 테스트 컨벤션. {@code @WebMvcTest} 는 {@code @RestControllerAdvice}(GlobalExceptionHandler)
 * 도 함께 로드하므로 공통 응답/검증 에러 포맷까지 검증할 수 있다.
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(SampleController.class)
class SampleControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private SampleService sampleService;

  @Test
  @DisplayName("샘플 생성 요청이 성공하면 201 과 공통 응답 포맷을 반환한다")
  void create_success() throws Exception {
    given(sampleService.create(any(CreateSampleCommand.class)))
        .willReturn(new SampleResponse(UUID.randomUUID(), "채소", LocalDateTime.now(), LocalDateTime.now()));

    mockMvc.perform(post("/api/v1/samples")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new CreateSampleRequestStub("채소"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("채소"));
  }

  @Test
  @DisplayName("이름이 비어 있으면 400 과 검증 에러 포맷을 반환한다")
  void create_validationFail() throws Exception {
    mockMvc.perform(post("/api/v1/samples")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new CreateSampleRequestStub(""))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("C-001"))
        .andExpect(jsonPath("$.error.fieldErrors[0].field").value("name"));
  }

  private record CreateSampleRequestStub(String name) {

  }
}
