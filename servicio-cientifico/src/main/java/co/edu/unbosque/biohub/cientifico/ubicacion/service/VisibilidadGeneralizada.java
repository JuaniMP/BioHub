package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import org.springframework.security.core.Authentication;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.FilaHallazgo;

/**
 * Estrategia por defecto (D41): solo departamento y región. NUNCA recibe el servicio de descifrado ni toca el
 * texto cifrado, así que ningún error de configuración o de rol puede hacerla devolver coordenadas.
 */
public final class VisibilidadGeneralizada implements EstrategiaVisibilidadUbicacion {

    @Override
    public UbicacionResuelta resolver(Authentication autenticacion, FilaHallazgo fila) {
        UbicacionResuelta.Estado estado = fila.tieneCoordenadas()
                ? UbicacionResuelta.Estado.PROTEGIDAS
                : UbicacionResuelta.Estado.SIN_COORDENADAS;
        return new UbicacionResuelta(estado, fila.departamento(), fila.departamentoCodigo(), fila.region(), null, null);
    }

    @Override
    public boolean necesitaCoordenadasCifradas() {
        return false;
    }
}
