package co.edu.unbosque.biohub.consultapublica.eventos.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.consultapublica.domain.Departamento;
import co.edu.unbosque.biohub.consultapublica.domain.EstadoConservacion;
import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;
import co.edu.unbosque.biohub.consultapublica.eventos.domain.DatosHallazgo;
import co.edu.unbosque.biohub.consultapublica.service.CatalogoDepartamentos;

/**
 * Valida el {@code datos} de un evento {@code hallazgo.*} y lo convierte en {@link FichaPublica} (D24, D32):
 * <ol>
 *   <li>cualquier clave de coordenadas, en cualquier nivel y sin distinguir mayúsculas, rechaza el evento;</li>
 *   <li>mapeo estricto de tipos + Bean Validation (longitudes, obligatorios);</li>
 *   <li>el departamento se resuelve en el catálogo (código o nombre); la región la deriva este servicio;</li>
 *   <li>estado ausente rechaza; estado desconocido se guarda como EN_PELIGRO (falla segura).</li>
 * </ol>
 * Los mensajes de error y los logs nunca incluyen valores del evento.
 */
@Component
public class ValidadorDatosHallazgo {

    private static final Logger log = LoggerFactory.getLogger(ValidadorDatosHallazgo.class);

    /** Claves prohibidas en minúsculas (D14, contrato 04 §7.3). */
    static final Set<String> CLAVES_PROHIBIDAS = Set.of("latitud", "longitud", "lat", "lon", "lng", "latitude",
            "longitude", "coordenadas", "coordinates", "geometry", "geom", "ubicacionexacta");

    private final ObjectMapper mapeador;
    private final Validator validador;
    private final CatalogoDepartamentos catalogo;

    public ValidadorDatosHallazgo(ObjectMapper objectMapper, Validator validador, CatalogoDepartamentos catalogo) {
        // Copia estricta: un número no se acepta como texto ni "1" como entero; los campos extra se ignoran
        this.mapeador = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, false)
                .configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, false);
        // Un número o booleano tampoco se acepta donde se espera texto
        for (CoercionInputShape forma : new CoercionInputShape[] {CoercionInputShape.Integer,
                CoercionInputShape.Float, CoercionInputShape.Boolean}) {
            this.mapeador.coercionConfigFor(LogicalType.Textual).setCoercion(forma, CoercionAction.Fail);
        }
        this.validador = validador;
        this.catalogo = catalogo;
    }

    /** @throws EventoInvalidoException si el evento debe ir a la DLQ */
    public FichaPublica validar(UUID eventoId, Map<String, Object> datos) {
        if (datos == null || datos.isEmpty()) {
            throw new EventoInvalidoException("Evento sin datos");
        }
        if (contieneClaveProhibida(datos)) {
            log.warn("Evento con coordenadas rechazado id={}", eventoId);
            throw new EventoInvalidoException("Evento con coordenadas no permitido");
        }

        DatosHallazgo d;
        try {
            d = mapeador.convertValue(datos, DatosHallazgo.class);
        } catch (IllegalArgumentException e) {
            throw new EventoInvalidoException("Evento con datos mal formados");
        }
        if (d == null) {
            throw new EventoInvalidoException("Evento sin datos");
        }
        Set<ConstraintViolation<DatosHallazgo>> violaciones = validador.validate(d);
        if (!violaciones.isEmpty()) {
            List<String> campos = violaciones.stream().map(v -> v.getPropertyPath().toString()).sorted().toList();
            throw new EventoInvalidoException("Evento con datos inválidos en: " + String.join(", ", campos));
        }

        Departamento departamento = catalogo.resolver(d.departamento())
                .orElseThrow(() -> new EventoInvalidoException("Evento con departamento desconocido"));

        return new FichaPublica(d.hallazgoId(), d.version(), d.nombreCientifico(), d.nombreComun(), d.familia(),
                d.tipoEspecimen(), d.fechaRecoleccion(), estado(eventoId, d.estadoConservacion()),
                departamento.codigo(), departamento.nombre(), departamento.region(), d.descripcion());
    }

    private static EstadoConservacion estado(UUID eventoId, String valor) {
        for (EstadoConservacion e : EstadoConservacion.values()) {
            if (e.name().equals(valor)) {
                return e;
            }
        }
        log.warn("Evento con estadoConservacion desconocido, se guarda EN_PELIGRO id={}", eventoId);
        return EstadoConservacion.EN_PELIGRO;
    }

    /** Recorre mapas y listas en cualquier profundidad buscando claves de coordenadas. */
    static boolean contieneClaveProhibida(Object nodo) {
        if (nodo instanceof Map<?, ?> mapa) {
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (e.getKey() != null && CLAVES_PROHIBIDAS.contains(e.getKey().toString().toLowerCase(Locale.ROOT))) {
                    return true;
                }
                if (contieneClaveProhibida(e.getValue())) {
                    return true;
                }
            }
        } else if (nodo instanceof Collection<?> coleccion) {
            List<Object> elementos = new ArrayList<>(coleccion);
            for (Object o : elementos) {
                if (contieneClaveProhibida(o)) {
                    return true;
                }
            }
        }
        return false;
    }
}
