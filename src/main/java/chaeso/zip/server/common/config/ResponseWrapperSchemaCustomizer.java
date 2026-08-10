package chaeso.zip.server.common.config;

import chaeso.zip.server.common.response.ApiResponse;
import io.swagger.v3.oas.models.media.ComposedSchema;
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
  private static final String CODE = "code";

  @Bean
  public OpenApiCustomizer responseWrapperRequiredFields() {
    return openApi -> {
      Map<String, Schema> schemas = openApi.getComponents().getSchemas();
      if (schemas == null) {
        return;
      }
      schemas.forEach((name, schema) -> {
        if (!name.startsWith(WRAPPER)) {
          return;
        }
        if (carriesPayload(name)) {
          require(schema, DATA);
          requireNullable(schema, ERROR);
        } else if (name.equals(WRAPPER)) {
          require(schema, ERROR);
          requireNullable(schema, DATA);
        } else {
          requireNullable(schema, DATA);
          requireNullable(schema, ERROR);
        }
        requireNullable(schema, CODE);
      });
    };
  }

  private void requireNullable(Schema<?> schema, String field) {
    markNullable(schema, field);
    require(schema, field);
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

  private void markNullable(Schema<?> schema, String field) {
    Schema<?> property = property(schema, field);
    if (property == null) {
      return;
    }
    if (property.get$ref() == null) {
      property.setNullable(true);
      return;
    }
    schema.getProperties().put(field, new ComposedSchema()
        .addAllOfItem(new Schema<>().$ref(property.get$ref()))
        .description(property.getDescription())
        .nullable(true));
  }

  private Schema<?> property(Schema<?> schema, String field) {
    return schema.getProperties() == null ? null : schema.getProperties().get(field);
  }
}
