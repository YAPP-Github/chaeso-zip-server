package chaeso.zip.server.common.config;

import chaeso.zip.server.common.exception.CommonErrorCode;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonResponsesCustomizer {

  private static final String ERROR_SCHEMA_REF =
      "#/components/schemas/" + ApiResponse.class.getSimpleName();

  private final Object internalServerErrorExample;
  private final Object unauthorizedExample;

  /**
   * GlobalExceptionHandler와 동일한 ObjectMapper로 변환해 예시가 실제 응답과 항상 일치하도록 한다.
   */
  public CommonResponsesCustomizer(ObjectMapper objectMapper) {
    this.internalServerErrorExample = errorExample(
        objectMapper, CommonErrorCode.INTERNAL_SERVER_ERROR);
    this.unauthorizedExample = errorExample(objectMapper, CommonErrorCode.UNAUTHORIZED);
  }

  @Bean
  public OperationCustomizer commonErrorResponses() {
    return (operation, handlerMethod) -> {
      ApiResponses responses = operation.getResponses();
      if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
        responses.computeIfAbsent("401", code -> errorResponse(
            "인증 필요(C-004)", "UNAUTHORIZED", unauthorizedExample));
      }
      responses.computeIfAbsent("500", code -> errorResponse(
          "서버 내부 오류", "INTERNAL_SERVER_ERROR", internalServerErrorExample));
      return operation;
    };
  }

  private Object errorExample(ObjectMapper objectMapper, CommonErrorCode errorCode) {
    ApiResponse<Void> response = ApiResponse.fail(ErrorResponse.of(errorCode));
    return objectMapper.convertValue(response, Object.class);
  }

  private io.swagger.v3.oas.models.responses.ApiResponse errorResponse(
      String description, String exampleName, Object exampleValue) {
    Content content = new Content().addMediaType(
        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
        new MediaType()
            .schema(new Schema<>().$ref(ERROR_SCHEMA_REF))
            .addExamples(exampleName, new Example().value(exampleValue)));
    return new io.swagger.v3.oas.models.responses.ApiResponse()
        .description(description)
        .content(content);
  }
}
