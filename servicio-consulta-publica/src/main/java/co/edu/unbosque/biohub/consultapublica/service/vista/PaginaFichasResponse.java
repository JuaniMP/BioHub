package co.edu.unbosque.biohub.consultapublica.service.vista;

import java.util.List;

/** Respuesta paginada del listado público (contrato 04, §4.1). */
public record PaginaFichasResponse(List<FichaResumenResponse> content, int page, int size, long total,
                                   int totalPaginas, boolean modoDegradado, List<String> filtrosIgnorados) {
}
