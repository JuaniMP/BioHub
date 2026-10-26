package co.edu.unbosque.biohub.consultapublica.eventos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;
import co.edu.unbosque.biohub.consultapublica.domain.ResultadoProyeccion;
import co.edu.unbosque.biohub.consultapublica.eventos.domain.EventoSobre;
import co.edu.unbosque.biohub.consultapublica.service.InvalidadorCacheFichas;
import co.edu.unbosque.biohub.consultapublica.service.ProyectorFichas;

/**
 * Procesa un evento {@code hallazgo.*} (registrado, reclasificado, actualizado): los tres llevan el mismo
 * snapshot y se tratan igual. Valida, proyecta en una transacción y, solo si hubo cambio y YA con el commit
 * hecho (este método no es transaccional), invalida las cachés (D25).
 */
@Service
public class ProcesadorHallazgos {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorHallazgos.class);

    private final ValidadorDatosHallazgo validador;
    private final ProyectorFichas proyector;
    private final InvalidadorCacheFichas invalidador;

    public ProcesadorHallazgos(ValidadorDatosHallazgo validador, ProyectorFichas proyector,
                               InvalidadorCacheFichas invalidador) {
        this.validador = validador;
        this.proyector = proyector;
        this.invalidador = invalidador;
    }

    public ResultadoProyeccion procesar(EventoSobre evento) {
        FichaPublica ficha = validador.validar(evento.id(), evento.datos());
        ResultadoProyeccion resultado = proyector.aplicar(ficha);
        if (resultado.huboCambio()) {
            invalidador.invalidar(ficha.hallazgoId());
            log.info("Ficha {} eventoId={} tipo={} hallazgoId={} version={}", resultado, evento.id(), evento.tipo(),
                    ficha.hallazgoId(), ficha.version());
        } else {
            log.debug("Evento ignorado (duplicado o versión antigua) eventoId={} hallazgoId={} version={}",
                    evento.id(), ficha.hallazgoId(), ficha.version());
        }
        return resultado;
    }
}
