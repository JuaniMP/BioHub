package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.cientifico.ubicacion.controller.FeatureResponse;
import co.edu.unbosque.biohub.cientifico.ubicacion.controller.MapaResponse;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;

/** Mapa (HU-07): GeoJSON con coordenadas exactas, solo para alto rango. Máximo 1.000 features. */
@Service
public class MapaService {

    static final String ENDPOINT = "GET /api/cientifico/ubicacion/mapa";
    static final int LIMITE = 1000;

    private final ConsultorHallazgos consultor;
    private final CatalogoDepartamentos catalogo;
    private final PoliticaAccesoUbicacion politica;
    private final ServicioDescifrado descifrado;
    private final AuditoriaAccesoUbicacion auditoria;

    MapaService(ConsultorHallazgos consultor, CatalogoDepartamentos catalogo, PoliticaAccesoUbicacion politica,
                ServicioDescifrado descifrado, AuditoriaAccesoUbicacion auditoria) {
        this.consultor = consultor;
        this.catalogo = catalogo;
        this.politica = politica;
        this.descifrado = descifrado;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public MapaResponse mapa(Authentication autenticacion, String especie, String departamento, String desde,
                             String hasta, String hallazgoId) {
        politica.exigirCoordenadas(autenticacion); // además del @PreAuthorize del endpoint

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

        long total = consultor.contar(criterio);
        List<FilaHallazgo> filas = consultor.buscar(criterio, 0, LIMITE + 1, true);
        boolean truncado = total > LIMITE;
        if (filas.size() > LIMITE) {
            filas = filas.subList(0, LIMITE);
        }

        List<FeatureResponse> features = new ArrayList<>(filas.size());
        int omitidos = 0;
        for (FilaHallazgo f : filas) {
            Optional<Coordenadas> c = descifrado.descifrar(autenticacion, f.id(), f.cifradas());
            if (c.isEmpty()) {
                omitidos++;
                continue;
            }
            features.add(FeatureResponse.punto(f.id(), c.get().longitud(), c.get().latitud(),
                    new FeatureResponse.Propiedades(f.id(), f.nombreCientifico(), f.fechaRecoleccion(),
                            f.investigador(), f.departamento(), f.departamentoCodigo(), f.region(), f.estado().name())));
        }
        auditoria.registrar(autenticacion.getName(), ENDPOINT, true, criterio.resumen(), features.size(), omitidos);
        return new MapaResponse("FeatureCollection", features, total, features.size(), truncado, omitidos, LIMITE);
    }
}
