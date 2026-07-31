package chaeso.zip.server.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

class RateLimitInterceptorTest {

  static class SampleController {

    @RateLimited("test-rule")
    public void limited() {
      // HandlerMethod 리플렉션 대상, 실행되지 않는다.
    }

    public void unlimited() {
      // HandlerMethod 리플렉션 대상, 실행되지 않는다.
    }
  }

  private RateLimiter rateLimiter;
  private RateLimitInterceptor interceptor;
  private HttpServletRequest request;
  private HttpServletResponse response;

  @BeforeEach
  void setUp() {
    rateLimiter = mock(RateLimiter.class);
    RateLimitProperties properties = new RateLimitProperties(
        Map.of("test-rule", new RateLimitProperties.RuleConfig(5, Duration.ofMinutes(1))));
    interceptor = new RateLimitInterceptor(rateLimiter, properties);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    given(request.getRemoteAddr()).willReturn("203.0.113.10");
  }

  private HandlerMethod handlerMethodFor(String methodName) throws NoSuchMethodException {
    return new HandlerMethod(new SampleController(),
        SampleController.class.getMethod(methodName));
  }

  @Nested
  @DisplayName("@RateLimited 요청 처리")
  class PreHandleTest {

    @Test
    @DisplayName("@RateLimited 애노테이션이 없는 메서드는 한도 검사 없이 요청을 통과시킨다")
    void noAnnotation_passesThrough() throws Exception {
      boolean result = interceptor.preHandle(request, response, handlerMethodFor("unlimited"));

      assertThat(result).isTrue();
      verify(rateLimiter, never()).tryConsume(anyString(), any());
    }

    @Test
    @DisplayName("한도 이내의 요청은 정상 통과시킨다")
    void withinLimit_passesThrough() throws Exception {
      given(rateLimiter.tryConsume(anyString(), any())).willReturn(RateLimitResult.allow());

      boolean result = interceptor.preHandle(request, response, handlerMethodFor("limited"));

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("한도를 초과한 요청은 RateLimitExceededException 예외를 발생시킨다")
    void overLimit_throws() throws Exception {
      given(rateLimiter.tryConsume(anyString(), any()))
          .willReturn(RateLimitResult.deny(Duration.ofSeconds(10)));
      HandlerMethod handlerMethod = handlerMethodFor("limited");

      assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
          .isInstanceOf(RateLimitExceededException.class);
    }
  }
}