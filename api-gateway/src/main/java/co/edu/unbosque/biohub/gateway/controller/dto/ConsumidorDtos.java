package co.edu.unbosque.biohub.gateway.controller.dto;

import java.time.Instant;
import java.util.UUID;

import co.edu.unbosque.biohub.gateway.domain.ApiKey;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorExterno;
import co.edu.unbosque.biohub.gateway.domain.EstadoApiKey;
import co.edu.unbosque.biohub.gateway.service.ServicioApiKeys;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpos JSON de la administración de consumidores y API Keys (04-contrato-api.md). */
public final class ConsumidorDtos {

    private ConsumidorDtos() {
    }

    public record CrearConsumidorRequest(
            @NotBlank(message = "obligatorio") @Size(max = 150, message = "máximo 150 caracteres") String nombre,
            @NotBlank(message = "obligatorio") @Email(message = "correo inválido")
            @Size(max = 254, message = "máximo 254 caracteres") String contactoEmail,
            @Min(value = 1, message = "debe estar entre 1 y 100000")
            @Max(value = 100_000, message = "debe estar entre 1 y 100000") Integer limitePorMinuto) {
    }

    /** {@code limitePorMinuto = null} vuelve al límite por defecto. */
    public record CambiarLimiteRequest(
            @Min(value = 1, message = "debe estar entre 1 y 100000")
            @Max(value = 100_000, message = "debe estar entre 1 y 100000") Integer limitePorMinuto) {
    }

    public record EmitirKeyRequest(@Future(message = "debe ser una fecha futura") Instant expiraEn) {
    }

    public record ConsumidorResponse(UUID id, String nombre, String contactoEmail, Integer limitePorMinuto,
                                     int limiteEfectivo, Instant creadoEn) {
        public static ConsumidorResponse de(ConsumidorExterno c, int limitePorDefecto) {
            int efectivo = c.getLimitePorMinuto() != null ? c.getLimitePorMinuto() : limitePorDefecto;
            return new ConsumidorResponse(c.getId(), c.getNombre(), c.getContactoEmail(), c.getLimitePorMinuto(),
                    efectivo, c.getCreadoEn());
        }
    }

    /** Nunca incluye la key ni su hash. */
    public record ApiKeyResponse(UUID id, String prefijo, EstadoApiKey estado, Instant expiraEn, Instant ultimoUsoEn,
                                 Instant creadaEn, Instant revocadaEn) {
        public static ApiKeyResponse de(ApiKey k) {
            return new ApiKeyResponse(k.getId(), k.getPrefijo(), k.getEstado(), k.getExpiraEn(), k.getUltimoUsoEn(),
                    k.getCreadaEn(), k.getRevocadaEn());
        }
    }

    /** Única respuesta que contiene la key completa. */
    public record KeyEmitidaResponse(UUID id, String apiKey, String prefijo, Instant expiraEn, Instant creadaEn) {
        public static KeyEmitidaResponse de(ServicioApiKeys.KeyEmitida emitida) {
            ApiKey k = emitida.registro();
            return new KeyEmitidaResponse(k.getId(), emitida.key(), k.getPrefijo(), k.getExpiraEn(), k.getCreadaEn());
        }

        @Override
        public String toString() {
            return "KeyEmitidaResponse[id=" + id + ", apiKey=***]";
        }
    }
}
