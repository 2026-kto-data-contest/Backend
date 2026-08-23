package com.jeontongjuro.backend.security.config;

import com.jeontongjuro.backend.security.filter.SessionAuthenticationFilter;
import com.jeontongjuro.backend.security.handler.RestAccessDeniedHandler;
import com.jeontongjuro.backend.security.handler.RestAuthenticationEntryPoint;
import com.jeontongjuro.backend.security.session.AuthProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/api/v1/auth/kakao",
            "/api/v1/auth/kakao/callback",
            "/api/v1/auth/csrf",
            "/api/v1/breweries",
            "/api/v1/breweries/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
    };

    private final SessionAuthenticationFilter sessionAuthenticationFilter;
    private final AuthProperties authProperties;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .secure(authProperties.cookieSecure())
                .sameSite(authProperties.cookieSameSite()));

        return http
                .cors(cors -> { })
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        // JT_SESSION은 매 요청마다 직접 인증하므로 기본 전략이 매번 CSRF 쿠키를
                        // "로그인 성공 후 폐기" 대상으로 오인하지 않도록 세션 전략을 비활성화한다.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
