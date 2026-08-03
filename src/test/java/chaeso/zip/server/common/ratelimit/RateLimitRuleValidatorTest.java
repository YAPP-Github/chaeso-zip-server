package chaeso.zip.server.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class RateLimitRuleValidatorTest {

  static class SampleController {

    @RateLimited("known-rule")
    public void known() {
      // HandlerMethod 리플렉션 대상, 실행되지 않는다.
    }

    @RateLimited("missing-rule")
    public void unknown() {
      // HandlerMethod 리플렉션 대상, 실행되지 않는다.
    }
  }

  @Nested
  @DisplayName("기동 시 규칙 검증")
  class OnApplicationEventTest {

    @Test
    @DisplayName("@RateLimited가 참조하는 규칙이 yaml 설정에 모두 존재하면 정상적으로 기동한다")
    void allRulesDefined_passes() throws Exception {
      RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
      HandlerMethod known = new HandlerMethod(new SampleController(),
          SampleController.class.getMethod("known"));
      given(handlerMapping.getHandlerMethods())
          .willReturn(Map.of(mock(RequestMappingInfo.class), known));
      RateLimitProperties properties = new RateLimitProperties(
          Map.of("known-rule", new RateLimitProperties.RuleConfig(5, Duration.ofMinutes(1), true)),
          Duration.ofSeconds(5));
      RateLimitRuleValidator validator = new RateLimitRuleValidator(handlerMapping, properties);

      assertThatCode(() -> validator.onApplicationEvent(mock(ContextRefreshedEvent.class)))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("@RateLimited가 참조하는 규칙이 yaml 설정에 없으면 기동 시점에 예외(IllegalStateException)를 던진다")
    void missingRule_throwsAtStartup() throws Exception {
      RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
      HandlerMethod unknown = new HandlerMethod(new SampleController(),
          SampleController.class.getMethod("unknown"));
      given(handlerMapping.getHandlerMethods())
          .willReturn(Map.of(mock(RequestMappingInfo.class), unknown));
      RateLimitProperties properties = new RateLimitProperties(Map.of(), Duration.ofSeconds(5));
      RateLimitRuleValidator validator = new RateLimitRuleValidator(handlerMapping, properties);

      assertThatThrownBy(() -> validator.onApplicationEvent(mock(ContextRefreshedEvent.class)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("missing-rule");
    }
  }
}
