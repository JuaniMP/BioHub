package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.cientifico.ubicacion.controller.HallazgoUbicacionResponse;
import co.edu.unbosque.biohub.cientifico.ubicacion.controller.PaginaHallazgosResponse;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;

/** Búsqueda interna (HU-13): filtros combinables, paginación y descifrado solo de la página para alto rango. */
@Service
public class BusquedaHallazgosService {

    static final String ENDPOINT = "GET /api/cientifico/ubicacion/hallazgos";
    static final int MAX_SIZE = 100;

    private final ConsultorHallazgos consultor;
    private final CatalogoDepartamentos catalogo;
    private final PoliticaAccesoUbicacion politica;
    private final ServicioDescifrado descifrado;
    private final AuditoriaAccesoUbicacion auditoria;

    BusquedaHallazgosService(ConsultorHallazgos consultor, CatalogoDepartamentos catalogo,
                             PoliticaAccesoUbicacion politica, ServicioDescifrado descifrado,
                             AuditoriaAccesoUbicacion auditoria) {
        this.consultor = consultor;
        this.catalogo = catalogo;
        this.politica = politica;
        this.descifrado = descifrado;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public PaginaHallazgosResponse buscar(Authentication autenticacion, String especie, String desde, String hasta,
                                          String investigador, String departamento, String page, String size,
                                          String bbox) {
        boolean puedeVer = politica.puedeVerCoordenadas(autenticacion);
        if (ValidadorConsulta.recortar(bbox) != null) {
            if (!puedeVer) {
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
        long total = consultor.contar(criterio);
        List<FilaHallazgo> filas = consultor.buscar(criterio, pag[0] * pag[1], pag[1], puedeVer);

        List<HallazgoUbicacionResponse> contenido = new ArrayList<>(filas.size());
        int omitidos = 0;
        for (FilaHallazgo fila : filas) {
            HallazgoUbicacionResponse r = convertir(autenticacion, puedeVer, fila);
            if ("NO_DISPONIBLES".equals(r.estadoCoordenadas())) {
                omitidos++;
            }
            contenido.add(r);
        }
        auditoria.registrar(autenticacion.getName(), ENDPOINT, true,
                criterio.resumen() + ",coordenadas=" + puedeVer, contenido.size(), omitidos);
        long totalPaginas = (total + pag[1] - 1) / pag[1];
        return new PaginaHallazgosResponse(contenido, pag[0], pag[1], total, totalPaginas, omitidos);
    }

    private HallazgoUbicacionResponse convertir(Authentication auth, boolean puedeVer, FilaHallazgo f) {
        String estado;
        Double lat = null;
        Double lon = null;
        if (!f.tieneCoordenadas()) {
            estado = "SIN_COORDENADAS";
        } else if (!puedeVer) {
            estado = "PROTEGIDAS";
        } else {
            Optional<Coordenadas> c = descifrado.descifrar(auth, f.id(), f.cifradas());
            if (c.isPresent()) {
                estado = "VISIBLES";
                lat = c.get().latitud();
                lon = c.get().longitud();
            } else {
                estado = "NO_DISPONIBLES";
            }
        }
        return new HallazgoUbicacionResponse(f.id(), f.nombreCientifico(), f.fechaRecoleccion(), f.investigador(),
                f.departamento(), f.departamentoCodigo(), f.region(), f.estado().name(), estado,
                "PROTEGIDAS".equals(estado), lat, lon);
    }
}
