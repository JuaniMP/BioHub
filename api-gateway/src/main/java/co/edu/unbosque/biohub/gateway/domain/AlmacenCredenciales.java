package co.edu.unbosque.biohub.gateway.domain;

import java.util.Optional;

/** Puerto para obtener la credencial de una key sin que el servicio sepa de dónde sale (BD o caché). */
public interface AlmacenCredenciales {

    Optional<CredencialApiKey> porPrefijo(String prefijo);

    /** Se llama al revocar una key o cambiar el límite: la siguiente petición debe ver el cambio. */
    void invalidar(String prefijo);
}
