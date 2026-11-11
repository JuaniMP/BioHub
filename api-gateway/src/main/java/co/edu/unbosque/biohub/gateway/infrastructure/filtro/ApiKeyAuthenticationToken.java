package co.edu.unbosque.biohub.gateway.infrastructure.filtro;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import co.edu.unbosque.biohub.gateway.domain.ConsumidorAutenticado;

/** Identidad de un consumidor externo cuya API Key ya se validó. Tiene el rol API_CLIENT. */
public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final transient ConsumidorAutenticado consumidor;

    public ApiKeyAuthenticationToken(ConsumidorAutenticado consumidor) {
        super(List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
        this.consumidor = consumidor;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return consumidor;
    }

    @Override
    public String getName() {
        return consumidor.prefijo();
    }
}
