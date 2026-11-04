package co.edu.unbosque.biohub.consultapublica.soporte;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Constructores de eventos {@code hallazgo.*} simulados (el publicador real es de otro equipo). */
public final class Eventos {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Eventos() {
    }

    /** {@code datos} válido de ejemplo (contrato 04, §7.4). */
    public static Map<String, Object> datos(UUID hallazgoId, int version, String nombreCientifico, String estado) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("hallazgoId", hallazgoId.toString());
        d.put("version", version);
        d.put("nombreCientifico", nombreCientifico);
        d.put("nombreComun", "Rana arlequín de Boyacá");
        d.put("familia", "Bufonidae");
        d.put("tipoEspecimen", "Adulto");
        d.put("fechaRecoleccion", "2026-03-14");
        d.put("estadoConservacion", estado);
        d.put("departamento", "15");
        d.put("descripcion", "Anfibio endémico de páramo");
        return d;
    }

    public static String sobre(String tipo, String origen, Map<String, Object> datos) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", UUID.randomUUID().toString());
        s.put("tipo", tipo);
        s.put("origen", origen);
        s.put("fecha", "2026-10-26T15:04:05.123Z");
        s.put("usuario", "investigador1");
        s.put("datos", datos);
        try {
            return MAPPER.writeValueAsString(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sobre(String tipo, Map<String, Object> datos) {
        return sobre(tipo, "servicio-cientifico", datos);
    }

    /**
     * Mensaje AMQP como lo publica servicio-cientifico (JSON persistente con cabecera __TypeId__ ajena y
     * {@code user_id} = usuario AMQP con que se conecta al broker, obligatorio según el contrato, §7).
     */
    public static Message mensaje(String json) {
        return mensaje(json, Contenedores.usuarioAmqp());
    }

    /** Igual que {@link #mensaje(String)} con un {@code user_id} explícito ({@code null} = sin user_id). */
    public static Message mensaje(String json, String userId) {
        MessageProperties p = new MessageProperties();
        p.setUserId(userId);
        p.setContentType("application/json");
        p.setHeader("__TypeId__", "co.edu.unbosque.biohub.cientifico.eventos.domain.EventoSobre");
        return MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).andProperties(p).build();
    }
}
