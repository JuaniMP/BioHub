package co.edu.unbosque.biohub.gateway.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import co.edu.unbosque.biohub.gateway.domain.RolInterno;
import co.edu.unbosque.biohub.gateway.infrastructure.filtro.ApiKeyRateLimitFilter;
import co.edu.unbosque.biohub.gateway.infrastructure.filtro.EscritorProblema;
import co.edu.unbosque.biohub.gateway.infrastructure.filtro.RevocacionTokenFilter;

/**
 * El gateway valida el JWT ANTES de reenviar (primera barrera). Cada servicio lo vuelve a validar
 * (defensa en profundidad), así que el gateway no es el único punto de control.
 *
 * <p>Aquí solo va el control grueso (SEC-02): quién puede entrar a cada prefijo. Qué rol puede usar cada
 * endpoint lo decide el servicio dueño, siguiendo la matriz de 04-contrato-api.md. Lo que no esté
 * permitido de forma explícita se deniega.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize("hasRole(...)") en los services/controllers
public class SecurityConfig {

    static final String ROL_ADMINISTRADOR = RolInterno.ADMINISTRADOR.nombre();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RutasGateway rutas,
                                            ApiKeyRateLimitFilter apiKeyRateLimitFilter,
                                            RevocacionTokenFilter revocacionTokenFilter,
                                            EscritorProblema escritor) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless con Bearer token, sin cookies de sesión
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Deja llegar los errores (400/500) al cliente en vez de convertirlos en 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Portal público sin login (D16): ApiKeyRateLimitFilter lo limita por IP.
                        .requestMatchers(rutas.portal()).permitAll()
                        // API externa: ApiKeyRateLimitFilter da este rol solo a una key válida (falla cerrado).
                        .requestMatchers(rutas.apiExterna()).hasRole("API_CLIENT")
                        .requestMatchers("/api/gateway/consumidores/**", "/api/gateway/keys/**",
                                "/api/gateway/usuarios/**").hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, "/api/gateway/ping").hasAnyRole(RolInterno.nombres())
                        // HU-10 c.2: un usuario autenticado sin rol tampoco entra a los módulos internos.
                        .requestMatchers(rutas.prefijosInternos()).hasAnyRole(RolInterno.nombres())
                        .anyRequest().denyAll())
                .addFilterBefore(apiKeyRateLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(revocacionTokenFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(resolverDeToken(rutas))
                        .accessDeniedHandler((peticion, respuesta, error) ->
                                escritor.rolInsuficiente(peticion, respuesta))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())));
        return http.build();
    }

    /** La API externa se autentica solo con API Key: un Authorization en esas rutas se ignora. */
    private static BearerTokenResolver resolverDeToken(RutasGateway rutas) {
        DefaultBearerTokenResolver normal = new DefaultBearerTokenResolver();
        return peticion -> rutas.esApiExterna(peticion) ? null : normal.resolve(peticion);
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
    FilterRegistrationBean<RevocacionTokenFilter> revocacionTokenFilterRegistration(RevocacionTokenFilter filtro) {
        FilterRegistrationBean<RevocacionTokenFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.origenesPermitidos());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-API-Key"));
        config.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER, "X-RateLimit-Limit", "X-RateLimit-Remaining"));
        config.setAllowCredentials(false); // se usa Bearer token, no cookies
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
