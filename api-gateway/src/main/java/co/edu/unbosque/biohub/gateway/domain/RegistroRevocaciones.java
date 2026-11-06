package co.edu.unbosque.biohub.gateway.domain;

import java.time.Instant;

/** Recuerda a qué usuarios les cambió el rol, para rechazar sus tokens anteriores al cambio. */
public interface RegistroRevocaciones {

    void marcar(String usuarioId);

    /** true si el token se emitió antes del último cambio de rol del usuario. */
    boolean revocado(String usuarioId, Instant emitidoEn);
}
