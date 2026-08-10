package chaeso.zip.server.common.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.annotation.Annotation;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NullableSchemaConfig {

  @Bean
  PropertyCustomizer nullableRefPropertyCustomizer() {
    return (property, type) -> {
      io.swagger.v3.oas.annotations.media.Schema declared = declaredSchema(type);
      if (property == null || property.get$ref() == null
          || declared == null || !declared.nullable()) {
        return property;
      }
      String description = property.getDescription() != null
          ? property.getDescription()
          : emptyToNull(declared.description());
      return new ComposedSchema()
          .addAllOfItem(new Schema<>().$ref(property.get$ref()))
          .description(description)
          .nullable(true);
    };
  }

  @Bean
  OpenApiCustomizer componentNullableStripper() {
    return openApi -> {
      if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
        return;
      }
      openApi.getComponents().getSchemas().values()
          .forEach(schema -> schema.setNullable(null));
    };
  }

  private static io.swagger.v3.oas.annotations.media.Schema declaredSchema(AnnotatedType type) {
    if (type == null || type.getCtxAnnotations() == null) {
      return null;
    }
    for (Annotation annotation : type.getCtxAnnotations()) {
      if (annotation instanceof io.swagger.v3.oas.annotations.media.Schema schema) {
        return schema;
      }
    }
    return null;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
