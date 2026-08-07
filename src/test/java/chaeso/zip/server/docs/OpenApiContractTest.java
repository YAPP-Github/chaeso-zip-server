package chaeso.zip.server.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import chaeso.zip.server.auth.infrastructure.security.SecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.AntPathMatcher;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

  private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";
  private static final String API_RESPONSE_REF = SCHEMA_REF_PREFIX + "ApiResponse";
  private static final String API_RESPONSE_VOID_REF = SCHEMA_REF_PREFIX + "ApiResponseVoid";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Nested
  @DisplayName("OpenAPI 기본 구조")
  class BasicDocumentContracts {

    @Test
    @DisplayName("유효한 3.0.1 스펙과 고유한 operationId를 노출한다")
    void apiDocsContractIsValid() throws Exception {
      JsonNode spec = loadOpenApiSpec();

      assertThat(spec.path("openapi").asText()).isEqualTo("3.0.1");
      assertThat(spec.path("paths").isObject()).isTrue();
      JsonNode bearerAuth = spec.path("components")
          .path("securitySchemes")
          .path("bearerAuth");
      assertThat(bearerAuth.path("type").asText()).isEqualTo("http");
      assertThat(bearerAuth.path("scheme").asText()).isEqualTo("bearer");
      assertThat(bearerAuth.path("bearerFormat").asText()).isEqualTo("JWT");
      assertThat(spec.path("security").isMissingNode() || spec.path("security").isEmpty())
          .isTrue();

      List<String> operationIds = collectOperationIds(spec);
      assertThat(operationIds).isNotEmpty();
      assertThat(new HashSet<>(operationIds))
          .as("operationId가 중복되면 codegen 메서드명이 불안정해집니다: %s", operationIds)
          .hasSameSizeAs(operationIds);
    }

    @Test
    @DisplayName("Pageable은 개별 쿼리 파라미터로 노출한다")
    void pageableParametersAreExploded() throws Exception {
      JsonNode spec = loadOpenApiSpec();

      forEachOperation(spec, (path, method, operationId, operation) -> {
        JsonNode parameters = operation.path("parameters");
        if (!parameters.isArray()) {
          return;
        }
        for (JsonNode parameter : parameters) {
          assertThat(parameter.path("name").asText())
              .as("%s의 Pageable은 @ParameterObject로 분리해야 합니다", operationId)
              .isNotEqualTo("pageable");
        }
      });
    }

    @Test
    @DisplayName("본문이 있는 모든 응답은 application/json으로 노출한다")
    void responseBodiesUseApplicationJson() throws Exception {
      JsonNode spec = loadOpenApiSpec();

      forEachResponse(spec, (operationId, responseCode, response) -> {
        JsonNode content = response.path("content");
        if (content.isMissingNode() || content.isEmpty()) {
          return;
        }
        assertThat(content.has("application/json"))
            .as("%s %s 응답 본문은 application/json이어야 합니다", operationId, responseCode)
            .isTrue();
        assertThat(content.has("*/*"))
            .as("%s %s 응답에 */* media type이 남아 있으면 안 됩니다", operationId, responseCode)
            .isFalse();
      });
    }
  }

  @Nested
  @DisplayName("성공 응답 계약")
  class SuccessResponseContracts {

    @Test
    @DisplayName("본문이 있는 모든 2xx 응답은 unknown이 아닌 구체 data schema를 노출한다")
    void successResponseBodiesUseConcreteDataSchemas() throws Exception {
      JsonNode spec = loadOpenApiSpec();

      forEachResponse(spec, (operationId, responseCode, response) -> {
        JsonNode content = response.path("content");
        if (!responseCode.startsWith("2") || content.isMissingNode() || content.isEmpty()) {
          return;
        }

        JsonNode schema = content.path("application/json").path("schema");
        String responseRef = assertExistingSchemaRef(
            spec, schema, operationId + " " + responseCode + " 성공 응답");
        assertThat(responseRef)
            .as("%s %s 성공 응답은 raw ApiResponse를 사용하면 data가 unknown이 됩니다",
                operationId, responseCode)
            .isNotEqualTo(API_RESPONSE_REF);

        JsonNode wrapper = resolveSchemaRef(spec, responseRef);
        if (API_RESPONSE_VOID_REF.equals(responseRef)) {
          return;
        }

        JsonNode data = wrapper.path("properties").path("data");
        assertThat(data.isMissingNode() || data.isEmpty())
            .as("%s %s 응답 wrapper의 data에 구체 schema가 있어야 합니다",
                operationId, responseCode)
            .isFalse();
        assertConcreteDataSchema(spec, data, operationId + " " + responseCode + " data");
      });
    }

    @Test
    @DisplayName("examples가 있는 응답은 함께 사용할 schema를 노출한다")
    void responseExamplesAlwaysHaveSchemas() throws Exception {
      JsonNode spec = loadOpenApiSpec();

      forEachResponse(spec, (operationId, responseCode, response) ->
          response.path("content").forEach(mediaType -> {
            JsonNode examples = mediaType.path("examples");
            if (examples.isMissingNode() || examples.isEmpty()) {
              return;
            }
            assertThat(mediaType.path("schema").isMissingNode()
                || mediaType.path("schema").isEmpty())
                .as("%s %s examples가 있는 응답에는 schema도 있어야 합니다",
                    operationId, responseCode)
                .isFalse();
          }));
    }
  }

  @Nested
  @DisplayName("인증 및 무본문 응답 계약")
  class SecurityAndNoContentContracts {

    @Test
    @DisplayName("SecurityConfig 인증 대상 API는 bearerAuth와 공통 401 오류 schema를 노출한다")
    void bearerOperationsPublishCommonUnauthorizedResponse() throws Exception {
      JsonNode spec = loadOpenApiSpec();
      String[] publicPaths = publicPaths("PUBLIC_PATHS");
      String[] publicGetPaths = publicPaths("PUBLIC_GET_PATHS");
      AntPathMatcher pathMatcher = new AntPathMatcher();
      List<String> bearerOperations = new ArrayList<>();

      forEachOperation(spec, (path, method, operationId, operation) -> {
        // PUBLIC_GET_PATHS 는 GET 만 열려 있으므로 같은 경로의 다른 메서드는 인증 대상이다
        boolean publicApi = matchesAny(pathMatcher, publicPaths, path)
            || ("get".equals(method) && matchesAny(pathMatcher, publicGetPaths, path));
        if (publicApi) {
          return;
        }
        bearerOperations.add(operationId);

        assertThat(requiresBearerAuth(operation))
            .as("%s %s는 SecurityConfig 인증 대상이므로 bearerAuth가 있어야 합니다",
                operationId, path)
            .isTrue();
        JsonNode unauthorized = operation.path("responses").path("401");
        assertThat(unauthorized.isMissingNode())
            .as("%s는 bearerAuth API이므로 401 응답이 있어야 합니다", operationId)
            .isFalse();
        JsonNode schema = unauthorized.path("content").path("application/json").path("schema");
        assertThat(schema.path("$ref").asText())
            .as("%s의 401은 성공 DTO가 아닌 공통 오류 schema를 사용해야 합니다", operationId)
            .isEqualTo(API_RESPONSE_REF);
      });

      assertThat(bearerOperations)
          .as("bearerAuth 적용 API가 하나 이상 있어야 합니다")
          .isNotEmpty();
    }

    @Test
    @DisplayName("모든 204 응답은 content와 schema를 노출하지 않는다")
    void noContentResponsesHaveNoBody() throws Exception {
      JsonNode spec = loadOpenApiSpec();

      forEachResponse(spec, (operationId, responseCode, response) -> {
        if (!"204".equals(responseCode)) {
          return;
        }
        JsonNode content = response.path("content");
        assertThat(content.isMissingNode() || content.isEmpty())
            .as("%s 204 응답에는 content/schema가 없어야 합니다", operationId)
            .isTrue();
      });
    }

    @Test
    @DisplayName("페이로드가 있는 성공 응답 래퍼는 data를 required로 노출한다")
    void successWrappersRequireData() throws Exception {
      JsonNode spec = loadOpenApiSpec();
      JsonNode schemas = spec.path("components").path("schemas");
      List<String> checked = new ArrayList<>();

      schemas.properties().forEach(schema -> {
        String name = schema.getKey();
        // ApiResponse 는 제네릭이 지워진 오류 응답용, ApiResponseVoid 는 본문이 없는 성공 응답이다
        if (!name.startsWith("ApiResponse") || name.equals("ApiResponse")
            || name.equals("ApiResponseVoid")) {
          return;
        }
        checked.add(name);
        List<String> required = new ArrayList<>();
        schema.getValue().path("required").forEach(field -> required.add(field.asText()));
        assertThat(required)
            .as("%s 는 2xx 전용 래퍼이므로 data가 required 여야 합니다", name)
            .contains("data");
      });

      assertThat(checked)
          .as("페이로드를 담는 성공 응답 래퍼가 하나 이상 있어야 합니다")
          .isNotEmpty();
    }

    @Test
    @DisplayName("data가 없는 래퍼는 data를 required로 올리지 않는다")
    void wrappersWithoutDataDoNotRequireIt() throws Exception {
      JsonNode spec = loadOpenApiSpec();
      JsonNode schemas = spec.path("components").path("schemas");

      for (String name : List.of("ApiResponse", "ApiResponseVoid")) {
        assertThat(requiredFields(schemas.path(name)))
            .as("%s 는 data가 없는 응답에 쓰이므로 required 가 아니어야 합니다", name)
            .doesNotContain("data");
      }
    }

    @Test
    @DisplayName("오류 응답 래퍼는 error를 required로 노출한다")
    void errorWrapperRequiresError() throws Exception {
      JsonNode spec = loadOpenApiSpec();
      JsonNode schemas = spec.path("components").path("schemas");

      assertThat(requiredFields(schemas.path("ApiResponse")))
          .as("ApiResponse 는 오류 응답 전용이므로 error가 required 여야 합니다")
          .contains("error");
      assertThat(requiredFields(schemas.path("ApiResponseVoid")))
          .as("ApiResponseVoid 는 성공 응답이므로 error가 required 가 아니어야 합니다")
          .doesNotContain("error");
    }

    @Test
    @DisplayName("성공 응답은 페이로드 래퍼를, 오류 응답은 공통 래퍼를 참조한다")
    void wrapperSchemasSplitBySuccess() throws Exception {
      JsonNode spec = loadOpenApiSpec();

      // data/error 를 required 로 올릴 수 있는 근거가 이 갈림이다. 섞이면 둘 다 거짓이 된다
      forEachResponse(spec, (operationId, responseCode, response) -> {
        JsonNode ref =
            response.path("content").path("application/json").path("schema").path("$ref");
        if (ref.isMissingNode()) {
          return;
        }
        String schema = ref.asText().substring(SCHEMA_REF_PREFIX.length());
        if (!schema.startsWith(API_RESPONSE_REF.substring(SCHEMA_REF_PREFIX.length()))) {
          return;
        }
        if (responseCode.startsWith("2")) {
          assertThat(schema)
              .as("%s %s 성공 응답이 제네릭 없는 ApiResponse를 참조하면 data required가 깨집니다",
                  operationId, responseCode)
              .isNotEqualTo("ApiResponse");
        } else {
          assertThat(schema)
              .as("%s %s 오류 응답이 타입 래퍼를 참조하면 error required가 깨집니다",
                  operationId, responseCode)
              .isEqualTo("ApiResponse");
        }
      });
    }

    private List<String> requiredFields(JsonNode schema) {
      List<String> required = new ArrayList<>();
      schema.path("required").forEach(field -> required.add(field.asText()));
      return required;
    }
  }

  @Nested
  @DisplayName("DTO 스키마 계약")
  class DtoSchemaContracts {

    @Test
    @DisplayName("실제 null이 가능한 채널 필드는 nullable로 노출한다")
    void nullableChannelResponseFieldsArePublished() throws Exception {
      JsonNode spec = loadOpenApiSpec();

      assertNullableProperties(spec, "PricingResponse",
          "value", "valueMax", "unitPeriod", "unitDays", "segment", "validPeriod");
      assertNullableProperties(spec, "AudienceMetricResponse",
          "valueNumeric", "valueText", "unit", "period");
    }

    @Test
    @DisplayName("공유 컴포넌트 스키마는 nullable을 노출하지 않는다")
    void componentSchemasAreNeverNullable() throws Exception {
      JsonNode spec = loadOpenApiSpec();

      spec.path("components").path("schemas").properties().forEach(entry ->
          assertThat(entry.getValue().path("nullable").asBoolean())
              .as("%s는 nullable일 수 없습니다. 필드에는 nullable 대신 requiredMode = NOT_REQUIRED를 사용하세요.", entry.getKey())
              .isFalse());
    }
  }

  private void assertConcreteDataSchema(JsonNode spec, JsonNode schema, String location) {
    String ref = schema.path("$ref").asText();
    if (!ref.isBlank()) {
      assertExistingSchemaRef(spec, schema, location);
      JsonNode referenced = resolveSchemaRef(spec, ref);
      JsonNode content = referenced.path("properties").path("content");
      if (!content.isMissingNode()) {
        assertThat(content.path("type").asText())
            .as("%s.content는 배열이어야 합니다", location)
            .isEqualTo("array");
        assertConcreteDataSchema(spec, content.path("items"), location + ".content.items");
      }
      return;
    }

    String type = schema.path("type").asText();
    if ("array".equals(type)) {
      JsonNode items = schema.path("items");
      assertThat(items.isMissingNode() || items.isEmpty())
          .as("%s 배열에는 구체 items schema가 있어야 합니다", location)
          .isFalse();
      assertConcreteDataSchema(spec, items, location + ".items");
      return;
    }

    assertThat(type.isBlank()
        && !schema.has("oneOf")
        && !schema.has("allOf")
        && !schema.has("anyOf"))
        .as("%s가 빈 schema이면 openapi-typescript에서 unknown으로 생성됩니다", location)
        .isFalse();
  }

  private String assertExistingSchemaRef(JsonNode spec, JsonNode schema, String location) {
    String ref = schema.path("$ref").asText();
    assertThat(ref)
        .as("%s는 components.schemas의 $ref를 사용해야 합니다", location)
        .startsWith(SCHEMA_REF_PREFIX);
    assertThat(resolveSchemaRef(spec, ref).isMissingNode())
        .as("%s의 $ref %s가 실제 components.schemas에 존재해야 합니다", location, ref)
        .isFalse();
    return ref;
  }

  private JsonNode resolveSchemaRef(JsonNode spec, String ref) {
    if (!ref.startsWith(SCHEMA_REF_PREFIX)) {
      return objectMapper.missingNode();
    }
    return spec.at(ref.substring(1));
  }

  private void assertNullableProperties(JsonNode spec, String schemaName,
      String... propertyNames) {
    JsonNode properties = spec.path("components").path("schemas").path(schemaName)
        .path("properties");

    assertThat(properties.isMissingNode())
        .as("components.schemas.%s.properties가 있어야 합니다", schemaName)
        .isFalse();
    for (String propertyName : propertyNames) {
      assertThat(properties.path(propertyName).path("nullable").asBoolean())
          .as("%s.%s는 nullable이어야 합니다", schemaName, propertyName)
          .isTrue();
    }
  }

  private boolean requiresBearerAuth(JsonNode operation) {
    JsonNode security = operation.path("security");
    if (!security.isArray()) {
      return false;
    }
    for (JsonNode requirement : security) {
      if (requirement.has("bearerAuth")) {
        return true;
      }
    }
    return false;
  }

  private String[] publicPaths(String fieldName) {
    Object publicPaths = ReflectionTestUtils.getField(SecurityConfig.class, fieldName);
    assertThat(publicPaths)
        .as("SecurityConfig.%s를 확인할 수 있어야 합니다", fieldName)
        .isInstanceOf(String[].class);
    return (String[]) publicPaths;
  }

  private boolean matchesAny(AntPathMatcher pathMatcher, String[] patterns, String path) {
    return List.of(patterns).stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
  }

  private JsonNode loadOpenApiSpec() throws Exception {
    MvcResult result = mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private List<String> collectOperationIds(JsonNode spec) {
    List<String> operationIds = new ArrayList<>();
    forEachOperation(spec,
        (path, method, operationId, operation) -> operationIds.add(operationId));
    return operationIds;
  }

  private void forEachOperation(JsonNode spec, OperationConsumer consumer) {
    for (Map.Entry<String, JsonNode> path : spec.path("paths").properties()) {
      for (Map.Entry<String, JsonNode> method : path.getValue().properties()) {
        JsonNode operation = method.getValue();
        String operationId = operation.path("operationId").asText();
        if (!operationId.isBlank()) {
          consumer.accept(path.getKey(), method.getKey(), operationId, operation);
        }
      }
    }
  }

  private void forEachResponse(JsonNode spec, ResponseConsumer consumer) {
    forEachOperation(spec, (path, method, operationId, operation) ->
        operation.path("responses").properties().forEach(response ->
            consumer.accept(operationId, response.getKey(), response.getValue())));
  }

  @FunctionalInterface
  private interface OperationConsumer {

    void accept(String path, String method, String operationId, JsonNode operation);
  }

  @FunctionalInterface
  private interface ResponseConsumer {

    void accept(String operationId, String responseCode, JsonNode response);
  }
}
