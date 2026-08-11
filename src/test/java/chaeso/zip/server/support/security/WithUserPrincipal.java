package chaeso.zip.server.support.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * {@code UserPrincipal}로 인증한 상태를 만드는 테스트 어노테이션.
 * 클래스나 테스트 메서드에 붙일 수 있으며, {@code userId}로 사용자를 바꿀 수 있다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithUserPrincipalSecurityContextFactory.class)
public @interface WithUserPrincipal {

  /** 사용자 ID를 따로 지정하지 않았을 때 쓸 UUID 문자열. */
  String DEFAULT_USER_ID = "11111111-1111-1111-1111-111111111111";

  /** 인증할 사용자의 UUID 문자열. */
  String userId() default DEFAULT_USER_ID;
}
