package chaeso.zip.server.support.redis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * 지정한 포트에 embedded Redis를 띄우는 JUnit 5 테스트 어노테이션.
 * 서버 시작/종료와 데이터 초기화는 {@link EmbeddedRedisExtension}이 맡는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EmbeddedRedisExtension.class)
public @interface EmbeddedRedisTest {

  /** embedded Redis를 띄울 포트. */
  int port();
}
