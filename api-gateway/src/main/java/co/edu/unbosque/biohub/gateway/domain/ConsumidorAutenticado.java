package co.edu.unbosque.biohub.gateway.domain;

import java.util.UUID;

/**
 * Resultado de validar una API Key.
 *
 * @param limitePorMinuto límite propio del consumidor; null = usa el límite por defecto
 */
public record ConsumidorAutenticado(UUID consumidorId, UUID keyId, String prefijo, Integer limitePorMinuto) {
}
