package co.edu.unbosque.biohub.cientifico.ubicacion.service.vista;

import java.util.List;

public record PaginaHallazgosResponse(
        List<HallazgoUbicacionResponse> content,
        int page,
        int size,
        long total,
        long totalPaginas,
        int omitidos) {
}
