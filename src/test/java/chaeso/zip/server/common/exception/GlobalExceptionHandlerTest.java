package chaeso.zip.server.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.common.ratelimit.RateLimitExceededException;
import chaeso.zip.server.common.response.ApiResponse;
import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Nested
  @DisplayName("rate limit 초과 처리")
  class HandleRateLimitExceededTest {

    @Test
    @DisplayName("RateLimitExceededException 발생 시 HTTP 429 응답과 올림 처리된 초 단위 Retry-After 헤더를 반환한다")
    void handleRateLimitExceeded_setsRetryAfterHeader() {
      RateLimitExceededException exception = new RateLimitExceededException(Duration.ofMillis(1500));

      ResponseEntity<ApiResponse<Void>> response = handler.handleRateLimitExceeded(exception);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
      assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().getError().getCode()).isEqualTo("C-005");
    }
  }
}