package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

import java.util.Optional;
import java.util.Set;

import javax.crypto.SecretKey;

/** Puerto de acceso a las llaves versionadas. */
public interface ProveedorDeLlaves {

    String versionActiva();

    Optional<SecretKey> llave(String version);

    /** Ids de las versiones configuradas (sin las llaves). */
    Set<String> versiones();
}
