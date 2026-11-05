package co.edu.unbosque.biohub.cientifico.registro.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.edu.unbosque.biohub.cientifico.eventos.service.EventoNoPublicadoException;
import co.edu.unbosque.biohub.cientifico.eventos.service.PublicadorEventos;
import co.edu.unbosque.biohub.cientifico.registro.service.CambioHallazgoConfirmado;

/**
 * Anuncia al resto de servicios los cambios de hallazgos, <b>después</b> de que la transacción se
 * confirma (04-contrato-api §7.5, obligación 1 del publicador).
 *
 * <p>Publicar antes del commit anunciaría versiones que podrían no guardarse: la consulta pública
 * mostraría una ficha que no existe. Por eso este listener es {@code AFTER_COMMIT} y no participa en
 * la transacción.
 *
 * <p>Si el broker no confirma el mensaje, el hallazgo ya está guardado y no se revierte: lo correcto
 * es que el registro científico no dependa de la disponibilidad del broker. Queda un log de error con
 * el hallazgo y el tipo para poder republicar. La solución definitiva es una tabla outbox, que el
 * contrato recomienda y que queda como mejora pendiente.
 */
@Component
public class PublicadorCambiosHallazgo {

    private static final Logger log = LoggerFactory.getLogger(PublicadorCambiosHallazgo.class);

    private final PublicadorEventos publicadorEventos;

    public PublicadorCambiosHallazgo(PublicadorEventos publicadorEventos) {
        this.publicadorEventos = publicadorEventos;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(CambioHallazgoConfirmado cambio) {
        try {
            publicadorEventos.publicar(cambio.tipo(), cambio.usuario(), cambio.datos());
        } catch (EventoNoPublicadoException e) {
            // El cambio está guardado; solo falló el anuncio. Se registra para republicar manualmente.
            log.error("Hallazgo {} guardado pero su evento {} no se pudo publicar: hay que republicarlo",
                    cambio.datos().get("hallazgoId"), cambio.tipo());
        }
    }
}
