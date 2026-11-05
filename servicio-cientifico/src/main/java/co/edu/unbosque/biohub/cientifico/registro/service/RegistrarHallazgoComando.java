package co.edu.unbosque.biohub.cientifico.registro.service;

import java.time.LocalDate;
import java.util.Map;

import co.edu.unbosque.biohub.cientifico.registro.domain.CategoriaUicn;

/**
 * Datos para registrar un hallazgo nuevo (HU-01).
 *
 * <p>Las coordenadas llegan aquí solo de paso: se entregan a ubicación para que las cifre y no se
 * guardan en ninguna tabla, log, historial ni evento de este módulo (04-contrato-api §6, obligación 2).
 * Por eso {@link #toString()} las enmascara.
 *
 * @param latitud  ambas coordenadas o ninguna; {@code null} = hallazgo sin ubicación registrada
 * @param atributos valores de los atributos personalizados de la plantilla vigente (HU-02)
 */
public record RegistrarHallazgoComando(
        String nombreCientifico,
        String nombreComun,
        String familia,
        String tipoEspecimen,
        LocalDate fechaRecoleccion,
        String departamento,
        CategoriaUicn categoriaUicn,
        String descripcion,
        Double latitud,
        Double longitud,
        Map<String, Object> atributos) {

    @Override
    public String toString() {
        return "RegistrarHallazgoComando[nombreCientifico=" + nombreCientifico
                + ", fechaRecoleccion=" + fechaRecoleccion + ", coordenadas=***]";
    }
}
