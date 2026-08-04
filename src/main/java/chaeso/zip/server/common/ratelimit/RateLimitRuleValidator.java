package chaeso.zip.server.common.ratelimit;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 기동 시 {@link RateLimited} 가 참조하는 규칙 이름이
 * {@code app.rate-limit.rules} 에 모두 정의돼 있는지 검증한다.
 */
@Component
public class RateLimitRuleValidator implements ApplicationListener<ContextRefreshedEvent> {

  private final RequestMappingHandlerMapping handlerMapping;
  private final RateLimitProperties properties;

  public RateLimitRuleValidator(
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
      RateLimitProperties properties) {
    this.handlerMapping = handlerMapping;
    this.properties = properties;
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    handlerMapping.getHandlerMethods().values().stream()
        .map(handlerMethod -> handlerMethod.getMethodAnnotation(RateLimited.class))
        .filter(Objects::nonNull)
        .forEach(annotation -> properties.rule(annotation.value()));
  }
}
