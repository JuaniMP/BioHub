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
        http
                .csrf(csrf -> csrf.disable()) // API stateless con Bearer token, sin cookies de sesión
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Deja llegar los errores (400/500) al cliente en vez de convertirlos en 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Control grueso por prefijo: hace falta un JWT válido. El rol de cada endpoint lo exige su
                        // @PreAuthorize (lo verifica ArquitecturaSeguridadTest), de modo que un 403 por rol insuficiente
                        // llega al UbicacionExceptionHandler: problem+json ROL_INSUFICIENTE y auditoría (contrato 04 §5.1).
                        // No se usa hasAnyRole aquí: un JWT sin ningún rol interno recibiría un 403 vacío y sin auditar.
                        .requestMatchers("/api/cientifico/**").authenticated()
                        // Lo que no está en el prefijo se deniega, aunque el usuario tenga rol
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())));
        return http.build();
    }
}
