package co.edu.unbosque.biohub.consultapublica.eventos.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
 *   <li>cualquier clave de coordenadas (en cualquier nivel, sin distinguir mayúsculas) o texto con forma de
 *       coordenada hace que el evento se descarte sin pasar por la DLQ ({@link EventoConCoordenadasException});</li>
 *   <li>mapeo estricto de tipos + Bean Validation (longitudes, obligatorios, tope de {@code version});</li>
 *   <li>contenido plausible: fecha no futura ni anterior a 1800, sin caracteres de control (SEC-J-01);</li>
 *   <li>el departamento se resuelve en el catálogo (código o nombre); la región la deriva este servicio;</li>
 *   <li>estado ausente rechaza; estado desconocido se guarda como EN_PELIGRO (falla segura).</li>
 * </ol>
 * Los mensajes de error y los logs nunca incluyen valores del evento.
 */
@Component
public class ValidadorDatosHallazgo {

    private static final Logger log = LoggerFactory.getLogger(ValidadorDatosHallazgo.class);

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");
    /** Año mínimo plausible de una recolección registrada en BioHub. */
    static final int ANIO_MINIMO = 1800;

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
        // SEC-J-02/J-03: coordenadas en una clave O en texto libre (descripcion, nombres...). El consumidor descarta
        // estos eventos con ack (no van a la DLQ); aquí solo se señala. Nunca se registra ni se cita el valor.
        if (DetectorCoordenadas.contiene(datos)) {
            throw new EventoConCoordenadasException();
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

        validarContenidoEstricto(d);

        Departamento departamento = catalogo.resolver(d.departamento())
                .orElseThrow(() -> new EventoInvalidoException("Evento con departamento desconocido"));

        return new FichaPublica(d.hallazgoId(), d.version(), d.nombreCientifico(), d.nombreComun(), d.familia(),
                d.tipoEspecimen(), d.fechaRecoleccion(), estado(eventoId, d.estadoConservacion()),
                departamento.codigo(), departamento.nombre(), departamento.region(), d.descripcion());
    }

    /** SEC-J-01: validación estricta del payload más allá de los tipos (fecha plausible, sin caracteres de control). */
    private static void validarContenidoEstricto(DatosHallazgo d) {
        LocalDate hoyBogota = LocalDate.now(ZONA_BOGOTA);
        if (d.fechaRecoleccion().isAfter(hoyBogota.plusDays(1)) || d.fechaRecoleccion().getYear() < ANIO_MINIMO) {
            throw new EventoInvalidoException("Evento con datos inválidos en: fechaRecoleccion");
        }
        List<String> sospechosos = new ArrayList<>();
        comprobarTexto(sospechosos, "nombreCientifico", d.nombreCientifico(), false);
        comprobarTexto(sospechosos, "nombreComun", d.nombreComun(), false);
        comprobarTexto(sospechosos, "familia", d.familia(), false);
        comprobarTexto(sospechosos, "tipoEspecimen", d.tipoEspecimen(), false);
        comprobarTexto(sospechosos, "estadoConservacion", d.estadoConservacion(), false);
        comprobarTexto(sospechosos, "departamento", d.departamento(), false);
        comprobarTexto(sospechosos, "descripcion", d.descripcion(), true);
        if (!sospechosos.isEmpty()) {
            throw new EventoInvalidoException("Evento con datos inválidos en: " + String.join(", ", sospechosos));
        }
    }

    /** Rechaza caracteres de control y de dirección del texto (spoofing en el portal); permite saltos de línea si se indica. */
    private static void comprobarTexto(List<String> sospechosos, String campo, String valor, boolean permiteSaltos) {
        if (valor == null) {
            return;
        }
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            boolean salto = c == '\n' || c == '\r' || c == '\t';
            boolean control = Character.isISOControl(c) && !(permiteSaltos && salto);
            boolean direccion = (c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069');
            if (control || direccion) {
                sospechosos.add(campo);
                return;
            }
        }
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
}
