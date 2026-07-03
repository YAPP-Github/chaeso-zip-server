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

  private static final String[] PUBLIC_PATHS = {
      "/api/auth/**",
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
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
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
