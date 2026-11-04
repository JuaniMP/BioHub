package co.edu.unbosque.biohub.consultapublica.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Estrategia de degradación controlada (RNF1, HU-06.4): ignora los filtros avanzados {@code familia} y
 * {@code departamento} y lo avisa en la respuesta ({@code modoDegradado: true}, {@code filtrosIgnorados} con
 * solo los que se enviaron, contrato 04 §4.1). La búsqueda por {@code q} y la paginación siguen funcionando.
 */
public final class BusquedaDegradada implements EstrategiaBusqueda {

    @Override
    public String nombre() {
        return "degradada";
    }

    @Override
    public CriterioBusquedaPublica criterio(String q, String familia, String departamentoCodigo, int page, int size) {
        List<String> ignorados = new ArrayList<>();
        if (familia != null) {
            ignorados.add("familia");
        }
        if (departamentoCodigo != null) {
            ignorados.add("departamento");
        }
        return new CriterioBusquedaPublica(q, null, null, page, size, true, List.copyOf(ignorados));
    }
}
