package co.edu.unbosque.biohub.gateway.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import co.edu.unbosque.biohub.gateway.infrastructure.filtro.ApiKeyRateLimitFilter;

/**
 * El gateway valida el JWT ANTES de reenviar (primera barrera). Cada servicio lo vuelve a validar
 * (defensa en profundidad), así que el gateway no es el único punto de control.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize("hasRole(...)") en los services/controllers
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, GatewayProperties props,
                                            ApiKeyRateLimitFilter apiKeyRateLimitFilter) throws Exception {
        String[] publicas = props.rutasPublicas().stream()
                .map(prefijo -> "/api/" + prefijo + "/**")
                .toArray(String[]::new);
        http
                .csrf(csrf -> csrf.disable()) // API stateless con Bearer token, sin cookies de sesión
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Deja llegar los errores (400/500) al cliente en vez de convertirlos en 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, publicas).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(apiKeyRateLimitFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())));
        return http.build();
    }

    /** Rutas rechazadas por el firewall (path traversal, %2e, ;, etc.) → 400 en vez de 500/401. */
    @Bean
    RequestRejectedHandler requestRejectedHandler() {
        return new HttpStatusRequestRejectedHandler(400);
    }

    /** Evita que Spring Boot registre el filtro dos veces (ya está dentro de la cadena de seguridad). */
    @Bean
    FilterRegistrationBean<ApiKeyRateLimitFilter> apiKeyRateLimitFilterRegistration(ApiKeyRateLimitFilter filtro) {
        FilterRegistrationBean<ApiKeyRateLimitFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.origenesPermitidos());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-API-Key"));
        config.setAllowCredentials(false); // se usa Bearer token, no cookies
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
