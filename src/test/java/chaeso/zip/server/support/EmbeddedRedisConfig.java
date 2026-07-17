package chaeso.zip.server.support;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import redis.embedded.RedisServer;

/**
 * {@code @SpringBootTest} 컨텍스트에서 Redis를 실제로 호출하는 테스트용 embedded 서버.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedRedisConfig {

  @Bean(initMethod = "start", destroyMethod = "stop")
  RedisServer embeddedRedisServer(@Value("${spring.data.redis.port}") int port) throws IOException {
    return RedisServer.newRedisServer().port(port).build();
  }
}
