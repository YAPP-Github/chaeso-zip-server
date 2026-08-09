package chaeso.zip.server.support.redis;

import java.io.IOException;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

/**
 * 테스트에서 쓸 embedded Redis 서버와 Lettuce 연결을 관리한다.
 * 테스트마다 Redis를 비우고 필요한 곳에 {@link StringRedisTemplate}을 넣어 준다.
 */
public final class EmbeddedRedisExtension implements BeforeAllCallback, BeforeEachCallback,
    AfterAllCallback, ParameterResolver {

  private static final String RESOURCE_KEY = "embedded-redis-resource";

  @Override
  public void beforeAll(ExtensionContext context) throws IOException {
    ExtensionContext.Store store = store(context);
    if (store.get(RESOURCE_KEY) == null) {
      EmbeddedRedisTest configuration = configuration(context);
      store.put(RESOURCE_KEY, RedisResource.start(configuration.port()));
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    resource(context).flushAll();
  }

  @Override
  public void afterAll(ExtensionContext context) throws IOException {
    if (!context.getRequiredTestClass().equals(configuredTestClass(context))) {
      return;
    }

    RedisResource resource = store(context).remove(RESOURCE_KEY, RedisResource.class);
    if (resource != null) {
      resource.close();
    }
  }

  @Override
  public boolean supportsParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) {
    return parameterContext.getParameter().getType().equals(StringRedisTemplate.class);
  }

  @Override
  public StringRedisTemplate resolveParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) {
    RedisResource resource = store(extensionContext).get(RESOURCE_KEY, RedisResource.class);
    if (resource == null) {
      throw new ParameterResolutionException("Embedded Redis has not been started");
    }
    return resource.template();
  }

  private RedisResource resource(ExtensionContext context) {
    RedisResource resource = store(context).get(RESOURCE_KEY, RedisResource.class);
    if (resource == null) {
      throw new IllegalStateException("Embedded Redis has not been started");
    }
    return resource;
  }

  private ExtensionContext.Store store(ExtensionContext context) {
    return context.getRoot().getStore(
        ExtensionContext.Namespace.create(EmbeddedRedisExtension.class,
            configuredTestClass(context)));
  }

  private EmbeddedRedisTest configuration(ExtensionContext context) {
    return configuredTestClass(context).getAnnotation(EmbeddedRedisTest.class);
  }

  private Class<?> configuredTestClass(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    while (testClass != null) {
      if (testClass.isAnnotationPresent(EmbeddedRedisTest.class)) {
        return testClass;
      }
      testClass = testClass.getEnclosingClass();
    }
    throw new IllegalStateException("@EmbeddedRedisTest is missing");
  }

  private record RedisResource(
      RedisServer server,
      LettuceConnectionFactory connectionFactory,
      StringRedisTemplate template) {

    private static RedisResource start(int port) throws IOException {
      RedisServer server = RedisServer.newRedisServer().port(port).build();
      server.start();

      LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory("localhost", port);
      connectionFactory.afterPropertiesSet();
      StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
      template.afterPropertiesSet();
      return new RedisResource(server, connectionFactory, template);
    }

    private void flushAll() {
      try (RedisConnection connection = connectionFactory.getConnection()) {
        connection.serverCommands().flushAll();
      }
    }

    private void close() throws IOException {
      try {
        connectionFactory.destroy();
      } finally {
        server.stop();
      }
    }
  }
}
