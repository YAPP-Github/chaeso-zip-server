package chaeso.zip.server.common.security;

import chaeso.zip.server.common.exception.CommonErrorCode;
import chaeso.zip.server.common.response.ApiResponse;
import chaeso.zip.server.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private static final String[] PUBLIC_AUTH_POST_PATHS = {
      "/api/v1/auth/signup/email-code",
      "/api/v1/auth/signup/email-code/verify",
      "/api/v1/auth/signup",
      "/api/v1/auth/login",
      "/api/v1/auth/reissue",
      "/api/v1/auth/logout"
  };

  private static final String[] PUBLIC_PATHS = {
      "/swagger-ui/**",
      "/v3/api-docs/**",
      "/actuator/health",
      "/actuator/prometheus"
  };

  private final JwtTokenProvider jwtTokenProvider;
  private final ObjectMapper objectMapper;
  private final CorsProperties corsProperties;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // 인증 정보를 쿠키가 아닌 Authorization 헤더와 요청 본문으로 전달하므로 CSRF 보호를 사용하지 않음
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_POST_PATHS).permitAll()
            .requestMatchers(PUBLIC_PATHS).permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint()))
        .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(Arrays.stream(corsProperties.allowedOrigins().split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .toList());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  private AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, authException) -> {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType("application/json;charset=UTF-8");
      ErrorResponse error = ErrorResponse.of(CommonErrorCode.UNAUTHORIZED);
      objectMapper.writeValue(response.getWriter(), ApiResponse.fail(error));
    };
  }
}
