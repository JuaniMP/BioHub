package co.edu.unbosque.biohub.gateway.infrastructure.credenciales;

import java.util.Optional;

import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.gateway.domain.AlmacenCredenciales;
import co.edu.unbosque.biohub.gateway.domain.ApiKeyRepository;
import co.edu.unbosque.biohub.gateway.domain.CredencialApiKey;

/** Fuente de verdad de las credenciales: la base de datos del gateway. */
@Component
public class AlmacenCredencialesJpa implements AlmacenCredenciales {

    private final ApiKeyRepository keys;

    public AlmacenCredencialesJpa(ApiKeyRepository keys) {
        this.keys = keys;
    }

    @Override
    public Optional<CredencialApiKey> porPrefijo(String prefijo) {
        return keys.buscarCredencial(prefijo);
    }

    @Override
    public void invalidar(String prefijo) {
        // La BD ya tiene el cambio: no hay nada que invalidar.
    }
}
