package co.edu.unbosque.biohub.equipos.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Convierte realm_access.roles de Keycloak en authorities ROLE_* y usa preferred_username como nombre. */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Roles técnicos que Keycloak agrega por defecto y que no son roles de negocio. */
    private static final Set<String> ROLES_TECNICOS = Set.of("offline_access", "uma_authorization");

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = extraerRoles(jwt).stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol))
                .toList();
        String usuario = jwt.getClaimAsString("preferred_username");
        return new JwtAuthenticationToken(jwt, authorities, usuario != null ? usuario : jwt.getSubject());
    }

    static List<String> extraerRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .filter(rol -> !ROLES_TECNICOS.contains(rol) && !rol.startsWith("default-roles-"))
                .toList();
    }
}
