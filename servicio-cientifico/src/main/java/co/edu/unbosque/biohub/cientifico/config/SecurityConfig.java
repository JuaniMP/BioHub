package co.edu.unbosque.biohub.cientifico.config;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource server: valida el JWT emitido por Keycloak (firma, emisor, vigencia y audiencia).
 * Los roles del realm se convierten en authorities ROLE_&lt;rol&gt;.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize("hasRole(...)") en los services/controllers
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 401 y 403 en problem+json con no-store en todo el servicio (SEC-J-20, contrato 04 §5.1)
        ProblemaSeguridadHandler problema = new ProblemaSeguridadHandler();
        http
                .csrf(csrf -> csrf.disable()) // API stateless con Bearer token, sin cookies de sesión
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Deja llegar los errores (400/500) al cliente en vez de convertirlos en 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Control grueso por prefijo: hace falta un JWT válido. El rol de cada endpoint lo exige su
                        // @PreAuthorize (lo verifica ArquitecturaSeguridadTest). El 403 por rol insuficiente es siempre
                        // problem+json ROL_INSUFICIENTE (contrato 04 §5.1): en ubicacion lo emite y audita
                        // UbicacionExceptionHandler; en el resto, ProblemaSeguridadHandler (SEC-J-20).
                        // No se usa hasAnyRole aquí: un 403 de la URL no pasaría por la auditoría de ubicacion (D55).
                        .requestMatchers("/api/cientifico/**").authenticated()
                        // Lo que no está en el prefijo se deniega, aunque el usuario tenga rol
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(problema).accessDeniedHandler(problema))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(problema) // JWT inválido o vencido
                        .accessDeniedHandler(problema)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())));
        return http.build();
    }
}
