package co.edu.unbosque.biohub.consultapublica.service;

import java.util.List;

/** Estrategia normal: aplica la búsqueda por texto, los filtros y la paginación tal como llegaron. */
public final class BusquedaCompleta implements EstrategiaBusqueda {

    @Override
    public String nombre() {
        return "completa";
    }

    @Override
    public CriterioBusquedaPublica criterio(String q, String familia, String departamentoCodigo, int page, int size) {
        return new CriterioBusquedaPublica(q, familia, departamentoCodigo, page, size, false, List.of());
    }
}
