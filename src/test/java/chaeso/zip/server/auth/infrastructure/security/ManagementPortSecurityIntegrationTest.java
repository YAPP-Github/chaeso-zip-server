package chaeso.zip.server.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.auth.infrastructure.jwt.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class ManagementPortSecurityIntegrationTest {

  @LocalServerPort
  private int serverPort;

  @LocalManagementPort
  private int managementPort;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  @Test
  @DisplayName("actuator health는 관리 포트에서 인증 없이 응답한다")
  void actuatorHealthOnManagementPortIsPublic() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + managementPort + "/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("메인 포트에는 actuator health가 매핑되지 않는다")
  void actuatorHealthIsNotServedOnMainPort() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(jwtTokenProvider.createAccessToken(
        UUID.fromString("11111111-1111-1111-1111-111111111111")));

    ResponseEntity<String> response = restTemplate.exchange(
        "http://localhost:" + serverPort + "/actuator/health",
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
