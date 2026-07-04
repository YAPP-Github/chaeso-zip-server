package chaeso.zip.server.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

  private static final Path SNAPSHOT = Path.of("build", "openapi", "openapi.json");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  @DisplayName("/v3/api-docs 가 유효한 스펙을 반환하고 operationId 가 중복되지 않는다")
  void apiDocsContractIsValid() throws Exception {
    MvcResult result = mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode spec = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(spec.path("openapi").asText()).startsWith("3.");
    assertThat(spec.path("paths").isObject()).isTrue();

    List<String> operationIds = collectOperationIds(spec);
    assertThat(operationIds).isNotEmpty();
    Set<String> unique = new HashSet<>(operationIds);
    assertThat(unique)
        .as("operationId 가 중복되면 codegen 메서드명이 _1 처럼 불안정해진다: %s", operationIds)
        .hasSameSizeAs(operationIds);
    assertAuthOperationsArePublic(spec);

    writeSnapshot(spec);
  }

  private void assertAuthOperationsArePublic(JsonNode spec) {
    List<JsonNode> authOperations = new ArrayList<>();
    spec.path("paths").properties().forEach(entry -> {
      if (entry.getKey().startsWith("/api/v1/auth/")) {
        entry.getValue().forEach(authOperations::add);
      }
    });

    assertThat(authOperations).hasSize(6);
    assertThat(authOperations)
        .allSatisfy(operation -> {
          assertThat(operation.has("security")).isTrue();
          assertThat(operation.path("security").isArray()).isTrue();
          assertThat(operation.path("security")).isEmpty();
        });
  }

  private List<String> collectOperationIds(JsonNode spec) {
    List<String> ids = new ArrayList<>();
    spec.path("paths").forEach(pathItem ->
        pathItem.forEach(operation -> {
          JsonNode operationId = operation.get("operationId");
          if (operationId != null) {
            ids.add(operationId.asText());
          }
        }));
    return ids;
  }

  private void writeSnapshot(JsonNode spec) throws Exception {
    Files.createDirectories(SNAPSHOT.getParent());
    Files.writeString(SNAPSHOT, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec));
  }
}
