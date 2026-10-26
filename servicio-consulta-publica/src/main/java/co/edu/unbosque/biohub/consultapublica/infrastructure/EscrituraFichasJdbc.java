package co.edu.unbosque.biohub.consultapublica.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;

/** Escrituras del modelo de lectura. Nunca se guarda usuario ni investigador (HU-06.1). */
@Repository
public class EscrituraFichasJdbc {

    private final JdbcClient jdbc;

    public EscrituraFichasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** UPDATE atómico condicionado a que la versión guardada sea menor. Devuelve las filas cambiadas (0 o 1). */
    public int actualizarSiEsMasNueva(FichaPublica f) {
        return jdbc.sql("""
                        UPDATE ficha_publica SET version = ?, nombre_cientifico = ?, nombre_comun = ?, familia = ?,
                               tipo_especimen = ?, fecha_recoleccion = ?, estado_conservacion = ?,
                               departamento_codigo = ?, departamento_nombre = ?, region_nombre = ?, descripcion = ?
                        WHERE hallazgo_id = ? AND version < ?""")
                .param(f.version()).param(f.nombreCientifico()).param(f.nombreComun()).param(f.familia())
                .param(f.tipoEspecimen()).param(f.fechaRecoleccion()).param(f.estadoConservacion().name())
                .param(f.departamentoCodigo()).param(f.departamentoNombre()).param(f.regionNombre())
                .param(f.descripcion())
                .param(UuidBinario.aBytes(f.hallazgoId())).param(f.version())
                .update();
    }

    /** INSERT; lanza {@code DuplicateKeyException} si el hallazgo ya existe. */
    public void insertar(FichaPublica f) {
        jdbc.sql("""
                        INSERT INTO ficha_publica (hallazgo_id, version, nombre_cientifico, nombre_comun, familia,
                               tipo_especimen, fecha_recoleccion, estado_conservacion, departamento_codigo,
                               departamento_nombre, region_nombre, descripcion)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""")
                .param(UuidBinario.aBytes(f.hallazgoId())).param(f.version()).param(f.nombreCientifico())
                .param(f.nombreComun()).param(f.familia()).param(f.tipoEspecimen()).param(f.fechaRecoleccion())
                .param(f.estadoConservacion().name()).param(f.departamentoCodigo()).param(f.departamentoNombre())
                .param(f.regionNombre()).param(f.descripcion())
                .update();
    }
}
