package com.jangingmall.backend.global.config;

import tools.jackson.databind.ObjectMapper;
import com.jangingmall.backend.global.common.response.ApiErrorResponse;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.security.JwtAuthenticationFilter;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.global.security.AiCallbackFilter;
import com.jangingmall.backend.content.application.GenerationProperties;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import com.jangingmall.backend.member.infrastructure.MemberOAuthSecurity;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, AiProperties.class, InternalApiProperties.class, GenerationProperties.class})
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final String corsAllowedOrigins;

    public SecurityConfig(ObjectMapper objectMapper, Environment environment,
                          @Value("${cors.allowed-origins}") String corsAllowedOrigins) {
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Bean
    public AiCallbackFilter aiCallbackFilter(InternalApiProperties internalApiProperties) {
        return new AiCallbackFilter(internalApiProperties);
    }

    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        AiCallbackFilter aiCallbackFilter,
        ObjectProvider<MemberOAuthSecurity> memberOAuthSecurity
    ) throws Exception {
        if (memberOAuthSecurity.getIfAvailable() != null) {
            memberOAuthSecurity.getObject().configure(http);
        }
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    .requestMatchers("/internal/**").hasRole("AGENT")
                    .requestMatchers(HttpMethod.POST, "/api/images/presigned-url").hasAnyRole("USER", "ARTISAN", "AGENT")
                    .requestMatchers(PermitAllPaths.PATHS.toArray(String[]::new)).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/member/artisans", "/api/member/artisans/{artisanId:[0-9]+}",
                        "/api/member/artisans/{artisanId:[0-9]+}/products", "/api/member/artisans/{artisanId:[0-9]+}/reviews").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/payments/webhooks/toss").permitAll()
                    .requestMatchers(HttpMethod.PATCH, "/api/payments/cart/items/*/options").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/payments/cart/merge").authenticated()
                    .requestMatchers("/api/payments/cart", "/api/payments/cart/items", "/api/payments/cart/items/*").permitAll();
                if (isLocalProfile()) {
                    auth.requestMatchers("/dev/**").permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED)
                )
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN)
                )
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(aiCallbackFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(corsAllowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
        return new JwtTokenProvider(jwtProperties);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    private boolean isLocalProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (profile.startsWith("local")) return true;
        }
        return false;
    }

    private void writeError(HttpServletResponse response, int status, ErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(errorCode));
    }
}
