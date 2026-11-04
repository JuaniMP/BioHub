package co.edu.unbosque.biohub.consultapublica.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

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
    void elBrokerRechazaUnUserIdFalsificado() {
        Contenedores.crearUsuarioAmqp(INTRUSO + "2", CLAVE);
        CachingConnectionFactory cf = conexionComo(INTRUSO + "2", CLAVE);
        try {
            String json = Eventos.sobre("hallazgo.registrado",
                    Eventos.datos(id("falsificado"), 1, "Atelopus ebenoides", "NO_AMENAZADA"));
            assertThatThrownBy(() -> {
                plantillaDe(cf).send("biohub.eventos", "hallazgo.registrado",
                        Eventos.mensaje(json, Contenedores.usuarioAmqp()));
                // el error de canal llega de forma asíncrona: una operación posterior lo hace visible
                plantillaDe(cf).execute(ch -> ch.queueDeclarePassive("consulta-publica.eventos"));
            }).isNotNull();
        } finally {
            cf.destroy();
        }
    }
}
