package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.time.LocalDate;
import java.util.UUID;

/** latitud/longitud: ambas o ninguna (null = hallazgo sin coordenadas). */
public record RegistroUbicacionComando(
        UUID hallazgoId,
        int version,
        String nombreCientifico,
        LocalDate fechaRecoleccion,
        String investigador,                 // preferred_username del JWT de quien registra
        String departamento,                 // código DANE ("15") o nombre oficial ("Boyacá")
        EstadoConservacion estadoConservacion,
        Double latitud,
        Double longitud) {

    @Override
    public String toString() {               // nunca imprime coordenadas
        return "RegistroUbicacionComando[hallazgoId=" + hallazgoId + ", version=" + version + ", coordenadas=***]";
    }
}
