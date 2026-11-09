package co.edu.unbosque.biohub.gateway.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo necesario para validar una key en cada petición. Se puede guardar en caché: contiene el hash
 * de la key, nunca la key.
 */
public record CredencialApiKey(UUID keyId, UUID consumidorId, byte[] hash, EstadoApiKey estado,
                               Instant expiraEn, Integer limitePorMinuto) {

    public boolean utilizableEn(Instant instante) {
        return estado == EstadoApiKey.ACTIVA && (expiraEn == null || expiraEn.isAfter(instante));
    }
}
