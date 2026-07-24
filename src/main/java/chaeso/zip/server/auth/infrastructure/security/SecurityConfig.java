package chaeso.zip.server.auth.infrastructure.security;

import chaeso.zip.server.auth.infrastructure.jwt.JwtTokenProvider;
import chaeso.zip.server.common.exception.CommonErrorCode;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless Bearer Access Token 인증 기반 SecurityFilterChain 설정.
 * {@link #PUBLIC_PATHS}를 제외한 모든 요청은 인증을 요구하며, 인증 실패 시 공통 JSON 401({@code C-004})을 반환한다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
      "/swagger-ui/**",
      "/v3/api-docs/**",
      "/api/v1/auth/signup",
      "/api/v1/auth/signup/email-code",
      "/api/v1/auth/signup/email-code/verify",
      "/api/v1/auth/login",
      "/api/v1/auth/login/methods",
      "/api/v1/auth/refresh",
      "/api/v1/auth/google",
      "/api/v1/auth/google/link",
      "/api/v1/auth/signup/google",
      "/api/v1/channels",
      "/api/v1/channels/*"
  };

  private final JwtTokenProvider jwtTokenProvider;
  private final ObjectMapper objectMapper;
  private final CorsProperties corsProperties;

  @Bean
  @Order(1)
  @SuppressWarnings("java:S4502")
  SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher(EndpointRequest.toAnyEndpoint())
        // Actuator는 loopback 바인딩된 관리 포트에서만 접근하며 브라우저 세션을 사용하지 않는다.
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
    return http.build();
  }

  @Bean
  @Order(2)
  @SuppressWarnings("java:S4502")
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // Stateless Bearer 토큰 API이며 세션 쿠키 인증을 쓰지 않고 CORS credentials도 비활성화한다.
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(PUBLIC_PATHS).permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(exception ->
            exception.authenticationEntryPoint(authenticationEntryPoint()))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.allowedOrigins());
    configuration.setAllowedMethods(
        List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  private AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, exception) -> {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType("application/json;charset=UTF-8");
      ErrorResponse error = ErrorResponse.of(CommonErrorCode.UNAUTHORIZED);
      objectMapper.writeValue(response.getWriter(), ApiResponse.fail(error));
    };
  }
}
