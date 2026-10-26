package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Query;

/**
 * Filtros combinables ya validados. Genera solo las condiciones de los filtros recibidos y enlaza
 * siempre parámetros (nunca SQL/JPQL concatenado con valores del cliente).
 */
record CriterioBusquedaInterna(
        String especie,
        LocalDate desde,
        LocalDate hasta,
        String investigador,
        String departamentoCodigo,
        UUID hallazgoId,
        boolean soloConCoordenadas) {

    private static final char ESCAPE = '!';

    String clausulaWhere() {
        List<String> condiciones = new ArrayList<>();
        condiciones.add("h.eliminadoEn is null");
        if (hallazgoId != null) {
            condiciones.add("h.id = :hallazgoId");
        }
        if (soloConCoordenadas) {
            condiciones.add("h.coordCifrada is not null");
        }
        if (hallazgoId == null) {
            if (especie != null) {
                condiciones.add("h.nombreCientifico like :especie escape '" + ESCAPE + "'");
            }
            if (desde != null) {
                condiciones.add("h.fechaRecoleccion >= :desde");
            }
            if (hasta != null) {
                condiciones.add("h.fechaRecoleccion <= :hasta");
            }
            if (investigador != null) {
                condiciones.add("h.investigador = :investigador");
            }
            if (departamentoCodigo != null) {
                condiciones.add("h.departamentoCodigo = :departamento");
            }
        }
        return String.join(" and ", condiciones);
    }

    void enlazar(Query q) {
        if (hallazgoId != null) {
            q.setParameter("hallazgoId", hallazgoId);
            return;
        }
        if (especie != null) {
            q.setParameter("especie", escaparLike(especie) + "%");
        }
        if (desde != null) {
            q.setParameter("desde", desde);
        }
        if (hasta != null) {
            q.setParameter("hasta", hasta);
        }
        if (investigador != null) {
            q.setParameter("investigador", investigador);
        }
        if (departamentoCodigo != null) {
            q.setParameter("departamento", departamentoCodigo);
        }
    }

    static String escaparLike(String texto) {
        return texto.replace("" + ESCAPE, "" + ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
    }

    /** Resumen de los filtros para la auditoría (sin coordenadas). */
    String resumen() {
        List<String> partes = new ArrayList<>();
        if (hallazgoId != null) {
            partes.add("hallazgoId=" + hallazgoId);
        } else {
            if (especie != null) partes.add("especie=" + especie);
            if (desde != null) partes.add("desde=" + desde);
            if (hasta != null) partes.add("hasta=" + hasta);
            if (investigador != null) partes.add("investigador=" + investigador);
            if (departamentoCodigo != null) partes.add("departamento=" + departamentoCodigo);
        }
        return String.join(",", partes);
    }
}
