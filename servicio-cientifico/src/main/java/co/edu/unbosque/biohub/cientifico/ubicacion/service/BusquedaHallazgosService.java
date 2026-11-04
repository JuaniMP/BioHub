package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CriterioBusquedaInterna;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.FilaHallazgo;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.vista.HallazgoUbicacionResponse;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.vista.PaginaHallazgosResponse;

/**
 * Búsqueda interna (HU-13): filtros combinables, paginación y descifrado solo de la página para alto rango.
 * La visibilidad la decide la {@link EstrategiaVisibilidadUbicacion} que entrega la política (ARQ-09), no un {@code if}.
 */
@Service
public class BusquedaHallazgosService {

    static final String ENDPOINT = "GET /api/cientifico/ubicacion/hallazgos";
    static final int MAX_SIZE = 100;

    private final RepositorioUbicaciones repositorio;
    private final CatalogoDepartamentos catalogo;
    private final PoliticaAccesoUbicacion politica;
    private final AuditoriaAccesoUbicacion auditoria;

    BusquedaHallazgosService(RepositorioUbicaciones repositorio, CatalogoDepartamentos catalogo,
                             PoliticaAccesoUbicacion politica, AuditoriaAccesoUbicacion auditoria) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.politica = politica;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public PaginaHallazgosResponse buscar(Authentication autenticacion, String especie, String desde, String hasta,
                                          String investigador, String departamento, String page, String size,
                                          String bbox) {
        EstrategiaVisibilidadUbicacion estrategia = politica.estrategiaPara(autenticacion);
        if (ValidadorConsulta.recortar(bbox) != null) {
            if (!estrategia.necesitaCoordenadasCifradas()) {
                auditoria.registrar(autenticacion.getName(), ENDPOINT, false, "bbox", 0, 0);
                throw new AccessDeniedException("bbox solo para alto rango");
            }
            throw new FuncionNoSoportadaException("El filtro bbox aún no está soportado.");
        }

        ValidadorConsulta v = new ValidadorConsulta(catalogo);
        String esp = v.texto("especie", especie, 2, 100, "debe tener entre 2 y 100 caracteres");
        var d = v.fecha("desde", desde);
        var h = v.fecha("hasta", hasta);
        v.rangoFechas(d, h);
        String inv = v.texto("investigador", investigador, 1, 100, "debe tener entre 1 y 100 caracteres");
        String depto = v.departamento(departamento);
        int[] pag = v.paginacion(page, size, MAX_SIZE);
        v.lanzarSiHayErrores();

        CriterioBusquedaInterna criterio = new CriterioBusquedaInterna(esp, d, h, inv, depto, null, false);
        long total = repositorio.contar(criterio);
        List<FilaHallazgo> filas = repositorio.buscar(criterio, pag[0] * pag[1], pag[1],
                estrategia.necesitaCoordenadasCifradas());

        List<HallazgoUbicacionResponse> contenido = new ArrayList<>(filas.size());
        int omitidos = 0;
        for (FilaHallazgo fila : filas) {
            UbicacionResuelta u = estrategia.resolver(autenticacion, fila);
            if (u.estado() == UbicacionResuelta.Estado.NO_DISPONIBLES) {
                omitidos++;
            }
            contenido.add(aRespuesta(fila, u));
        }
        auditoria.registrar(autenticacion.getName(), ENDPOINT, true,
                criterio.resumen() + ",coordenadas=" + estrategia.necesitaCoordenadasCifradas(), contenido.size(), omitidos);
        long totalPaginas = (total + pag[1] - 1) / pag[1];
        return new PaginaHallazgosResponse(contenido, pag[0], pag[1], total, totalPaginas, omitidos);
    }

    private static HallazgoUbicacionResponse aRespuesta(FilaHallazgo f, UbicacionResuelta u) {
        return new HallazgoUbicacionResponse(f.id(), f.nombreCientifico(), f.fechaRecoleccion(), f.investigador(),
                u.departamento(), u.departamentoCodigo(), u.region(), f.estado().name(), u.estado().name(),
                u.protegida(), u.latitud(), u.longitud());
    }
}
