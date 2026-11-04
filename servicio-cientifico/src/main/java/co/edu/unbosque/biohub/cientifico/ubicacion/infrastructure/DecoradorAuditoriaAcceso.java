package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import java.util.UUID;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.AuditoriaAccesoUbicacion;

/**
 * Decorator de auditoría (SAD §6.3.2, D28): registra el usuario y el hallazgo de CADA consulta de coordenadas
 * exactas, tanto si se entregan como si fallan por cualquier causa. Va por fuera del de cifrado. La línea es la
 * del log dedicado {@code biohub.auditoria.ubicacion}, con el hallazgo y sin coordenadas; la fecha la pone el log.
 * Además de la línea agregada por petición que escriben los servicios. Es privada del paquete: solo
 * {@link ConfiguracionUbicacion} arma la cadena (D43, SEC-J-10).
 */
final class DecoradorAuditoriaAcceso extends DecoradorRepositorioUbicaciones {

    static final String ACCION = "obtenerUbicacionExacta";

    private final AuditoriaAccesoUbicacion auditoria;

    DecoradorAuditoriaAcceso(RepositorioUbicaciones envuelto, AuditoriaAccesoUbicacion auditoria) {
        super(envuelto);
        this.auditoria = auditoria;
    }

    @Override
    public Coordenadas obtenerUbicacionExacta(String usuario, UUID hallazgoId, CoordenadasCifradas cifradas) {
        try {
            Coordenadas c = envuelto.obtenerUbicacionExacta(usuario, hallazgoId, cifradas);
            auditoria.registrar(usuario, ACCION, true, "hallazgoId=" + hallazgoId, 1, 0);
            return c;
        } catch (RuntimeException e) {
            // Cualquier fallo (llave retirada, integridad, error inesperado) queda auditado como intento no entregado;
            // solo van el usuario y el hallazgo, nunca la excepción ni valores (SEC-J-10)
            auditoria.registrar(usuario, ACCION, true, "hallazgoId=" + hallazgoId, 0, 1);
            throw e;
        }
    }
}
