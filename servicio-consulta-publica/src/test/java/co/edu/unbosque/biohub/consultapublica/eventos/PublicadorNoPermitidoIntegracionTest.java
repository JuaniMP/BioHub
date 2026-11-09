package co.edu.unbosque.biohub.consultapublica.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.biohub.consultapublica.soporte.BaseIntegracion;
import co.edu.unbosque.biohub.consultapublica.soporte.Contenedores;
import co.edu.unbosque.biohub.consultapublica.soporte.Eventos;

/**
 * ARQ-13 / SEC-J-01 / SEC-D-03: la verificación del publicador está activa por defecto. Un hallazgo.* publicado por
 * un usuario AMQP distinto del esperado, o sin user_id, termina en la DLQ sin crear ficha. RabbitMQ impide falsificar
 * el user_id desde otra conexión, así que el "intruso" es un segundo usuario real del broker efímero.
 */
@Testcontainers(disabledWithoutDocker = true)
class PublicadorNoPermitidoIntegracionTest extends BaseIntegracion {

    private static final String INTRUSO = "intruso_mq";
    private static final String CLAVE = "intruso-clave";

    private long fichas() {
        return jdbc.sql("SELECT COUNT(*) FROM ficha_publica").query(Long.class).single();
    }

    private RabbitTemplate plantillaDe(CachingConnectionFactory cf) {
        return new RabbitTemplate(cf);
    }

    private CachingConnectionFactory conexionComo(String usuario, String clave) {
        RabbitMQContainer q = Contenedores.rabbit();
        CachingConnectionFactory cf = new CachingConnectionFactory(q.getHost(), q.getAmqpPort());
        cf.setUsername(usuario);
        cf.setPassword(clave);
        return cf;
    }

    @Test
    @DisplayName("un publicador distinto del esperado (user_id propio) va a la DLQ y no crea ficha")
    void publicadorDistintoDelEsperadoVaALaDlq() {
        Contenedores.crearUsuarioAmqp(INTRUSO, CLAVE);
        CachingConnectionFactory cf = conexionComo(INTRUSO, CLAVE);
        try {
            UUID id = id("intruso");
            String json = Eventos.sobre("hallazgo.registrado",
                    Eventos.datos(id, 1, "Atelopus ebenoides", "NO_AMENAZADA"));
            plantillaDe(cf).send("biohub.eventos", "hallazgo.registrado", Eventos.mensaje(json, INTRUSO));

            Message enDlq = siguienteDeLaDlq(Duration.ofSeconds(15));
            assertThat(enDlq).as("el evento del publicador no permitido debe ir a la DLQ").isNotNull();
            assertThat(fichas()).isZero();
        } finally {
            cf.destroy();
        }
    }

    @Test
    @DisplayName("un evento sin user_id va a la DLQ y no crea ficha")
    void eventoSinUserIdVaALaDlq() {
        String json = Eventos.sobre("hallazgo.registrado",
                Eventos.datos(id("sin-user-id"), 1, "Atelopus ebenoides", "NO_AMENAZADA"));
        rabbit.send("biohub.eventos", "hallazgo.registrado", Eventos.mensaje(json, null));

        assertThat(siguienteDeLaDlq(Duration.ofSeconds(15))).as("sin user_id debe ir a la DLQ").isNotNull();
        assertThat(fichas()).isZero();
    }

    @Test
    @DisplayName("RabbitMQ no deja falsificar el user_id desde otra conexión (el contrato lo da por garantizado)")
    void elBrokerRechazaUnUserIdFalsificado() throws Exception {
        // BUG-J18-1: el broker rechaza la publicación cerrando el canal (PRECONDITION_FAILED), pero el cierre llega
        // de forma asíncrona. Antes se hacía una operación posterior y se esperaba que fallara, lo que dependía de la
        // carrera entre ambos. Ahora se espera el evento que se quiere comprobar: el cierre del canal, con el motivo.
        Contenedores.crearUsuarioAmqp(INTRUSO + "2", CLAVE);
        RabbitMQContainer q = Contenedores.rabbit();
        com.rabbitmq.client.ConnectionFactory fabrica = new com.rabbitmq.client.ConnectionFactory();
        fabrica.setHost(q.getHost());
        fabrica.setPort(q.getAmqpPort());
        fabrica.setUsername(INTRUSO + "2");
        fabrica.setPassword(CLAVE);

        CompletableFuture<ShutdownSignalException> cierre = new CompletableFuture<>();
        // el canal lo cierra el broker: se cierra solo la conexión (cierra sus canales; close() del canal ya cerrado falla)
        try (com.rabbitmq.client.Connection conexion = fabrica.newConnection()) {
            Channel canal = conexion.createChannel();
            canal.addShutdownListener(cierre::complete);
            String json = Eventos.sobre("hallazgo.registrado",
                    Eventos.datos(id("falsificado"), 1, "Atelopus ebenoides", "NO_AMENAZADA"));
            // user_id de otro usuario (el esperado) en una conexión que no es la suya
            AMQP.BasicProperties propiedades = new AMQP.BasicProperties.Builder()
                    .contentType("application/json").userId(Contenedores.usuarioAmqp()).build();
            canal.basicPublish("biohub.eventos", "hallazgo.registrado", propiedades,
                    json.getBytes(StandardCharsets.UTF_8));

            // espera acotada del cierre (un solo evento, sin reintentos); si el broker aceptara el mensaje, vence
            ShutdownSignalException senal = cierre.get(15, TimeUnit.SECONDS);
            assertThat(senal.isHardError()).as("se cierra solo el canal, no la conexión").isFalse();
            assertThat(senal.getReason()).isInstanceOf(AMQP.Channel.Close.class);
            AMQP.Channel.Close motivo = (AMQP.Channel.Close) senal.getReason();
            assertThat(motivo.getReplyCode()).as("PRECONDITION_FAILED").isEqualTo(406);
            assertThat(motivo.getReplyText()).contains("user_id");
        }
        assertThat(fichas()).as("el evento falsificado no llegó a crear ficha").isZero();
    }
}
