package co.edu.unbosque.biohub.gateway.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** API Key de un consumidor. Solo guarda su hash: la key en claro existe únicamente al emitirla. */
@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "binary(16)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "consumidor_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
    private UUID consumidorId;

    @Column(name = "prefijo", nullable = false, updatable = false, length = 20)
    private String prefijo;

    @Column(name = "hash_sha256", nullable = false, updatable = false, columnDefinition = "binary(32)")
    private byte[] hashSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoApiKey estado;

    @Column(name = "expira_en")
    private Instant expiraEn;

    @Column(name = "ultimo_uso_en")
    private Instant ultimoUsoEn;

    @Column(name = "creada_por", nullable = false, updatable = false, length = 64)
    private String creadaPor;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private Instant creadaEn;

    @Column(name = "revocada_por", length = 64)
    private String revocadaPor;

    @Column(name = "revocada_en")
    private Instant revocadaEn;

    protected ApiKey() {
    }

    public ApiKey(UUID id, UUID consumidorId, String prefijo, byte[] hashSha256, Instant expiraEn,
                  String creadaPor, Instant ahora) {
        this.id = id;
        this.consumidorId = consumidorId;
        this.prefijo = prefijo;
        this.hashSha256 = hashSha256.clone();
        this.estado = EstadoApiKey.ACTIVA;
        this.expiraEn = expiraEn;
        this.creadaPor = creadaPor;
        this.creadaEn = ahora;
    }

    /** Idempotente: revocar una key ya revocada conserva quién y cuándo lo hizo primero. */
    public void revocar(String actor, Instant ahora) {
        if (estado == EstadoApiKey.REVOCADA) {
            return;
        }
        this.estado = EstadoApiKey.REVOCADA;
        this.revocadaPor = actor;
        this.revocadaEn = ahora;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConsumidorId() {
        return consumidorId;
    }

    public String getPrefijo() {
        return prefijo;
    }

    public EstadoApiKey getEstado() {
        return estado;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getUltimoUsoEn() {
        return ultimoUsoEn;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getRevocadaEn() {
        return revocadaEn;
    }
}
