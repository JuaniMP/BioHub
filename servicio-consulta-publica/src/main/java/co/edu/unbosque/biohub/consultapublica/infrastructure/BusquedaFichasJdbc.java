package co.edu.unbosque.biohub.consultapublica.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.biohub.consultapublica.service.vista.FichaPublicaResponse;
import co.edu.unbosque.biohub.consultapublica.service.vista.FichaResumenResponse;
import co.edu.unbosque.biohub.consultapublica.service.CriterioBusquedaPublica;

/**
 * Lecturas del modelo público con SQL dinámico y parámetros SIEMPRE enlazados (nunca concatenados): solo se
 * añaden los filtros recibidos. {@code MATCH ... AGAINST} no se expresa en JPQL, por eso JdbcClient (D26).
 * No selecciona ninguna columna que no sea pública.
 */
@Repository
public class BusquedaFichasJdbc {

    private static final String COLUMNAS_RESUMEN = """
            hallazgo_id, nombre_cientifico, nombre_comun, familia, estado_conservacion,
            departamento_nombre, departamento_codigo, region_nombre""";

    private final JdbcClient jdbc;

    public BusquedaFichasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<FichaResumenResponse> buscar(CriterioBusquedaPublica criterio) {
        List<Object> parametros = new ArrayList<>();
        String where = construirWhere(criterio, parametros);
        parametros.add(criterio.size());
        parametros.add(criterio.offset());
        return jdbc.sql("SELECT " + COLUMNAS_RESUMEN + " FROM ficha_publica" + where
                        + " ORDER BY nombre_cientifico, hallazgo_id LIMIT ? OFFSET ?")
                .params(parametros)
                .query((rs, n) -> new FichaResumenResponse(
                        UuidBinario.aUuid(rs.getBytes("hallazgo_id")),
                        rs.getString("nombre_cientifico"),
                        rs.getString("nombre_comun"),
                        rs.getString("familia"),
                        rs.getString("estado_conservacion"),
                        rs.getString("departamento_nombre"),
                        rs.getString("departamento_codigo"),
                        rs.getString("region_nombre")))
                .list();
    }

    public long contar(CriterioBusquedaPublica criterio) {
        List<Object> parametros = new ArrayList<>();
        String where = construirWhere(criterio, parametros);
        return jdbc.sql("SELECT COUNT(*) FROM ficha_publica" + where)
                .params(parametros)
                .query(Long.class)
                .single();
    }

    public Optional<FichaPublicaResponse> porId(UUID id) {
        return jdbc.sql("""
                        SELECT hallazgo_id, nombre_cientifico, nombre_comun, familia, tipo_especimen,
                               fecha_recoleccion, estado_conservacion, departamento_nombre, departamento_codigo,
                               region_nombre, descripcion
                        FROM ficha_publica WHERE hallazgo_id = ?""")
                .param(UuidBinario.aBytes(id))
                .query((rs, n) -> FichaPublicaResponse.protegida(
                        UuidBinario.aUuid(rs.getBytes("hallazgo_id")),
                        rs.getString("nombre_cientifico"),
                        rs.getString("nombre_comun"),
                        rs.getString("familia"),
                        rs.getString("tipo_especimen"),
                        rs.getObject("fecha_recoleccion", java.time.LocalDate.class),
                        rs.getString("estado_conservacion"),
                        rs.getString("departamento_nombre"),
                        rs.getString("departamento_codigo"),
                        rs.getString("region_nombre"),
                        rs.getString("descripcion")))
                .optional();
    }

    private static String construirWhere(CriterioBusquedaPublica c, List<Object> parametros) {
        List<String> condiciones = new ArrayList<>();
        if (c.q() != null) {
            var fulltext = c.expresionFulltext();
            if (fulltext.isPresent()) {
                condiciones.add("MATCH(nombre_cientifico, nombre_comun) AGAINST (? IN BOOLEAN MODE)");
                parametros.add(fulltext.get());
            } else {
                condiciones.add("nombre_cientifico LIKE ?");
                parametros.add(c.patronPrefijoLike());
            }
        }
        if (c.familia() != null) {
            condiciones.add("familia = ?");
            parametros.add(c.familia());
        }
        if (c.departamentoCodigo() != null) {
            condiciones.add("departamento_codigo = ?");
            parametros.add(c.departamentoCodigo());
        }
        return condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);
    }
}
