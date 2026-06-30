package chaeso.zip.server.common.config;

import chaeso.zip.server.common.response.ApiResponse;
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

  @Bean
  public OperationCustomizer commonErrorResponses() {
    return (operation, handlerMethod) -> {
      ApiResponses responses = operation.getResponses();
      responses.computeIfAbsent("500", code -> errorResponse("서버 내부 오류"));
      return operation;
    };
  }

  private io.swagger.v3.oas.models.responses.ApiResponse errorResponse(String description) {
    Content content = new Content().addMediaType(
        org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)));
    return new io.swagger.v3.oas.models.responses.ApiResponse()
        .description(description)
        .content(content);
  }
}
