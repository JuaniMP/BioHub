package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CriterioBusquedaInterna;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.FilaHallazgo;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.vista.FeatureResponse;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.vista.MapaResponse;

/** Mapa (HU-07): GeoJSON con coordenadas exactas, solo para alto rango. Máximo 1.000 features. */
@Service
public class MapaService {

    static final String ENDPOINT = "GET /api/cientifico/ubicacion/mapa";
    static final int LIMITE = 1000;

    private final RepositorioUbicaciones repositorio;
    private final CatalogoDepartamentos catalogo;
    private final PoliticaAccesoUbicacion politica;
    private final AuditoriaAccesoUbicacion auditoria;

    MapaService(RepositorioUbicaciones repositorio, CatalogoDepartamentos catalogo, PoliticaAccesoUbicacion politica,
                AuditoriaAccesoUbicacion auditoria) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.politica = politica;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public MapaResponse mapa(Authentication autenticacion, String especie, String departamento, String desde,
                             String hasta, String hallazgoId) {
        politica.exigirCoordenadas(autenticacion); // además del @PreAuthorize del endpoint
        EstrategiaVisibilidadUbicacion estrategia = politica.estrategiaPara(autenticacion); // exacta: solo alto rango

        ValidadorConsulta v = new ValidadorConsulta(catalogo);
        var id = v.uuid("hallazgoId", hallazgoId);
        CriterioBusquedaInterna criterio;
        if (ValidadorConsulta.recortar(hallazgoId) != null) {
            v.lanzarSiHayErrores();
            criterio = new CriterioBusquedaInterna(null, null, null, null, null, id, true);
        } else {
            String esp = v.texto("especie", especie, 2, 100, "debe tener entre 2 y 100 caracteres");
            var d = v.fecha("desde", desde);
            var h = v.fecha("hasta", hasta);
            v.rangoFechas(d, h);
            String depto = v.departamento(departamento);
            v.lanzarSiHayErrores();
            criterio = new CriterioBusquedaInterna(esp, d, h, null, depto, null, true);
        }

        long total = repositorio.contar(criterio);
        List<FilaHallazgo> filas = repositorio.buscar(criterio, 0, LIMITE + 1, estrategia.necesitaCoordenadasCifradas());
        boolean truncado = total > LIMITE;
        if (filas.size() > LIMITE) {
            filas = filas.subList(0, LIMITE);
        }

        List<FeatureResponse> features = new ArrayList<>(filas.size());
        int omitidos = 0;
        for (FilaHallazgo f : filas) {
            UbicacionResuelta u = estrategia.resolver(autenticacion, f);
            if (u.estado() != UbicacionResuelta.Estado.VISIBLES) {
                omitidos++;
                continue;
            }
            features.add(FeatureResponse.punto(f.id(), u.longitud(), u.latitud(),
                    new FeatureResponse.Propiedades(f.id(), f.nombreCientifico(), f.fechaRecoleccion(),
                            f.investigador(), u.departamento(), u.departamentoCodigo(), u.region(), f.estado().name())));
        }
        auditoria.registrar(autenticacion.getName(), ENDPOINT, true, criterio.resumen(), features.size(), omitidos);
        return new MapaResponse("FeatureCollection", features, total, features.size(), truncado, omitidos, LIMITE);
    }
}
