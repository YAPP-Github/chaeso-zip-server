package chaeso.zip.server.common.config;

import chaeso.zip.server.common.response.ApiResponse;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResponseWrapperSchemaCustomizer {

  private static final String WRAPPER = ApiResponse.class.getSimpleName();
  private static final String VOID_WRAPPER = WRAPPER + "Void";
  private static final String DATA = "data";
  private static final String ERROR = "error";

  @Bean
  public OpenApiCustomizer responseWrapperRequiredFields() {
    return openApi -> {
      Map<String, Schema> schemas = openApi.getComponents().getSchemas();
      if (schemas == null) {
        return;
      }
      schemas.forEach((name, schema) -> {
        if (carriesPayload(name)) {
          require(schema, DATA);
        } else if (name.equals(WRAPPER)) {
          require(schema, ERROR);
        }
      });
    };
  }

  private boolean carriesPayload(String schemaName) {
    return schemaName.startsWith(WRAPPER)
        && !schemaName.equals(WRAPPER)
        && !schemaName.equals(VOID_WRAPPER);
  }

  private void require(Schema<?> schema, String field) {
    boolean present = schema.getProperties() != null && schema.getProperties().containsKey(field);
    boolean alreadyRequired = schema.getRequired() != null && schema.getRequired().contains(field);
    if (present && !alreadyRequired) {
      schema.addRequiredItem(field);
    }
  }
}
