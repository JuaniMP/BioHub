package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Única política de quién puede ver coordenadas exactas (RF13, D21): solo {@code cientifico_alto_rango},
 * de cualquier especie. Es el contexto del Strategy (SAD §6.4.1, ARQ-09): {@link #estrategiaPara} elige la
 * {@link VisibilidadExacta} para el alto rango y, para cualquier otro caso (rol ausente, desconocido,
 * anónimo), la {@link VisibilidadGeneralizada}. Es pública porque la usa SpEL en {@code @PreAuthorize}
 * ({@code puedeVerCoordenadas}).
 */
@Component("politicaUbicacion")
public class PoliticaAccesoUbicacion {

    public static final String ROL_COORDENADAS = "ROLE_cientifico_alto_rango";

    private final EstrategiaVisibilidadUbicacion generalizada = new VisibilidadGeneralizada();
    // Perezoso: ServicioDescifrado exige esta política (segunda barrera) y la estrategia exacta necesita al servicio
    private final ObjectProvider<ServicioDescifrado> descifrado;
    private volatile EstrategiaVisibilidadUbicacion exacta;

    @Autowired
    public PoliticaAccesoUbicacion(ObjectProvider<ServicioDescifrado> descifrado) {
        this.descifrado = descifrado;
    }

    /** Solo para evaluar {@link #puedeVerCoordenadas} (sin servicio de descifrado, no puede dar la estrategia exacta). */
    public PoliticaAccesoUbicacion() {
        this.descifrado = null;
    }

    /** Estrategia para esta petición: generalizada por defecto; exacta solo para el alto rango. */
    public EstrategiaVisibilidadUbicacion estrategiaPara(Authentication autenticacion) {
        if (!puedeVerCoordenadas(autenticacion)) {
            return generalizada;
        }
        EstrategiaVisibilidadUbicacion actual = exacta;
        if (actual == null) {
            if (descifrado == null) {
                throw new IllegalStateException("La política no tiene servicio de descifrado");
            }
            actual = new VisibilidadExacta(descifrado.getObject());
            exacta = actual;
        }
        return actual;
    }

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
