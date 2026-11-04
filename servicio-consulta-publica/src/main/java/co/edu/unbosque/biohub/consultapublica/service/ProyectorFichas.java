package co.edu.unbosque.biohub.consultapublica.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;
import co.edu.unbosque.biohub.consultapublica.domain.ResultadoProyeccion;
import co.edu.unbosque.biohub.consultapublica.infrastructure.EscrituraFichasJdbc;

/**
 * Aplica un snapshot de hallazgo al modelo de lectura en una transacción. Gana la {@code version} mayor (D23):
 * un duplicado o un evento antiguo no cambia nada. La comparación y la escritura son una sola sentencia
 * ({@code UPDATE ... WHERE version < ?}), así que no hace falta bloquear la fila.
 * La invalidación de la caché la hace quien llama, DESPUÉS del commit.
 */
@Service
public class ProyectorFichas {

    private final EscrituraFichasJdbc escritura;

    public ProyectorFichas(EscrituraFichasJdbc escritura) {
        this.escritura = escritura;
    }

    /** Versión actualmente guardada del hallazgo (lectura informativa, sin bloqueo). */
    public java.util.Optional<Integer> versionGuardada(java.util.UUID hallazgoId) {
        return escritura.versionGuardada(hallazgoId);
    }

    @Transactional
    public ResultadoProyeccion aplicar(FichaPublica ficha) {
        if (escritura.actualizarSiEsMasNueva(ficha) > 0) {
            return ResultadoProyeccion.ACTUALIZADA;
        }
        try {
            escritura.insertar(ficha);
            return ResultadoProyeccion.CREADA;
        } catch (DuplicateKeyException existe) {
            // Ya existe con versión igual o mayor (o la insertó otro consumidor entre medias): se reintenta una vez
            return escritura.actualizarSiEsMasNueva(ficha) > 0
                    ? ResultadoProyeccion.ACTUALIZADA : ResultadoProyeccion.IGNORADA;
        }
    }
}
