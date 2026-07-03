package chaeso.zip.server.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi(Swagger UI) 설정. 문서 메타 정보를 정의한다.
 *
 * <p>Swagger UI: {@code /swagger-ui/index.html}, OpenAPI 문서: {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

  @Value("${spring.application.name:application}")
  private String applicationName;

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(new Info()
            .title(applicationName + " API")
            .description("채소.zip 서비스 API 문서")
            .version("v1")
            .contact(new Contact().name("chaeso-zip").email("channelsogae.zip@gmail.com"))
            .license(new License().name("Apache 2.0")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(new Components().addSecuritySchemes("bearerAuth",
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")));
  }
}
