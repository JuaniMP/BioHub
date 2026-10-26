package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Única política de quién puede ver coordenadas exactas (RF13, D21): solo {@code cientifico_alto_rango},
 * de cualquier especie. Es pública porque la usa SpEL en {@code @PreAuthorize}.
 */
@Component("politicaUbicacion")
public class PoliticaAccesoUbicacion {

    public static final String ROL_COORDENADAS = "ROLE_cientifico_alto_rango";

    public boolean puedeVerCoordenadas(Authentication autenticacion) {
        return autenticacion != null
                && autenticacion.isAuthenticated()
                && !(autenticacion instanceof AnonymousAuthenticationToken)
                && autenticacion.getAuthorities().stream().anyMatch(a -> ROL_COORDENADAS.equals(a.getAuthority()));
    }

    public void exigirCoordenadas(Authentication autenticacion) {
        if (!puedeVerCoordenadas(autenticacion)) {
            throw new AccessDeniedException("Sin permiso para ver coordenadas");
        }
    }
}
