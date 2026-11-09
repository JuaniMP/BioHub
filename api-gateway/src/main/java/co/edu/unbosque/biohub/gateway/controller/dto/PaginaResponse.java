package co.edu.unbosque.biohub.gateway.controller.dto;

import java.util.List;

/** Misma forma de paginación que el resto de la API (04-contrato-api.md §1). */
public record PaginaResponse<T>(List<T> content, int page, int size, long total, int totalPaginas) {

    public static <T> PaginaResponse<T> de(List<T> contenido, int pagina, int tamano, long total) {
        int paginas = (int) Math.ceil((double) total / tamano);
        return new PaginaResponse<>(contenido, pagina, tamano, total, paginas);
    }
}
