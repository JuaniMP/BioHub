package co.edu.unbosque.biohub.consultapublica.eventos.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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

    /** Diferencia de versión a partir de la cual se alerta (cada edición real suma 1; ver DatosHallazgo.VERSION_MAXIMA). */
    static final int SALTO_ANOMALO = 100;

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
        Optional<Integer> previa = versionPrevia(ficha);
        ResultadoProyeccion resultado = proyector.aplicar(ficha);
        if (resultado.huboCambio()) {
            invalidador.invalidar(ficha.hallazgoId());
            log.info("Ficha {} eventoId={} tipo={} hallazgoId={} version={}", resultado, evento.id(), evento.tipo(),
                    ficha.hallazgoId(), ficha.version());
            if (esSaltoAnomalo(previa.orElse(0), ficha.version())) {
                log.warn("ALERTA SEC-J-01: salto anómalo de version (de {} a {}) eventoId={} hallazgoId={}. "
                                + "Verificar el publicador: una versión desmesurada congela la ficha",
                        previa.orElse(0), ficha.version(), evento.id(), ficha.hallazgoId());
            }
        } else if (previa.isPresent() && esSaltoAnomalo(ficha.version(), previa.get())) {
            // Se ignora un evento muy anterior a la versión guardada: o está desordenado o la ficha quedó congelada
            log.warn("ALERTA SEC-J-01: evento ignorado con version {} muy inferior a la guardada {} eventoId={} "
                            + "hallazgoId={}. Si no es un reenvío antiguo, la ficha pudo quedar congelada por una versión falsa",
                    ficha.version(), previa.get(), evento.id(), ficha.hallazgoId());
        } else {
            log.debug("Evento ignorado (duplicado o versión antigua) eventoId={} hallazgoId={} version={}",
                    evento.id(), ficha.hallazgoId(), ficha.version());
        }
        return resultado;
    }

    private Optional<Integer> versionPrevia(FichaPublica ficha) {
        try {
            Optional<Integer> previa = proyector.versionGuardada(ficha.hallazgoId());
            return previa == null ? Optional.empty() : previa;
        } catch (DataAccessException e) {
            return Optional.empty(); // es solo informativo: la proyección real reportará el fallo de BD
        }
    }

    /** {@code true} si {@code nueva} supera a {@code anterior} en más de {@link #SALTO_ANOMALO}. */
    static boolean esSaltoAnomalo(int anterior, int nueva) {
        return (long) nueva - anterior > SALTO_ANOMALO;
    }
}
