package co.edu.unbosque.biohub.cientifico.registro.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una versión del hallazgo en el historial inmutable (HU-05, ASR7). Guarda el snapshot completo de
 * cómo quedó el hallazgo en esa versión, junto con quién lo cambió y por qué.
 *
 * <p><b>Patrón Builder</b> (SAD §6.2.1): la clase no expone ningún método de modificación y solo puede
 * construirse con {@link Builder}, cuyo {@link Builder#build()} verifica los obligatorios antes de crear
 * el objeto. Así ninguna versión llega a la base de datos incompleta o sin justificación, y una vez
 * creada no puede alterarse en memoria antes de persistirse. En la base de datos la inmutabilidad la
 * refuerzan el CHECK de la migración, los triggers y los permisos de solo INSERT y SELECT.
 *
 * <p>Los campos no se declaran {@code final} porque JPA necesita un constructor sin argumentos; la
 * inmutabilidad se garantiza por la ausencia total de setters y por el carácter append-only de la tabla.
 */
@Entity
@Table(name = "hallazgo_version")
public class VersionHallazgo {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "binary(16)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "hallazgo_id", nullable = false, columnDefinition = "binary(16)")
    private UUID hallazgoId;

    @Column(name = "version", nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false)
    private Map<String, Object> snapshot;

    @Column(name = "justificacion", length = 1000)
    private String justificacion;

    @Column(name = "autor", nullable = false, length = 100)
    private String autor;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private Instant creadoEn;

    protected VersionHallazgo() {
    }

    private VersionHallazgo(Builder builder) {
        this.id = builder.id;
        this.hallazgoId = builder.hallazgoId;
        this.version = builder.version;
        this.snapshot = builder.snapshot;
        this.justificacion = builder.justificacion;
        this.autor = builder.autor;
    }

    /** Primera versión de un hallazgo recién registrado: no exige justificación. */
    public static Builder primeraVersion(UUID hallazgoId) {
        return new Builder(hallazgoId, 1);
    }

    /**
     * Versión siguiente tras una reclasificación o edición (HU-03): el número sale de la versión anterior
     * y la justificación pasa a ser obligatoria.
     */
    public static Builder siguienteVersion(UUID hallazgoId, int versionAnterior) {
        return new Builder(hallazgoId, versionAnterior + 1);
    }

    public UUID getId() {
        return id;
    }

    public UUID getHallazgoId() {
        return hallazgoId;
    }

    public int getVersion() {
        return version;
    }

    public Map<String, Object> getSnapshot() {
        return snapshot == null ? Map.of() : Map.copyOf(snapshot);
    }

    public String getJustificacion() {
        return justificacion;
    }

    public String getAutor() {
        return autor;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    @Override
    public String toString() {
        return "VersionHallazgo[hallazgoId=" + hallazgoId + ", version=" + version + "]";
    }

    /**
     * Construye una {@link VersionHallazgo} paso a paso y valida en {@link #build()} que esté completa.
     * Desde la versión 2 exige justificación y autor (HU-03, RF9).
     */
    public static final class Builder {

        private final UUID id = UUID.randomUUID();
        private final UUID hallazgoId;
        private final int version;
        private Map<String, Object> snapshot;
        private String justificacion;
        private String autor;

        private Builder(UUID hallazgoId, int version) {
            this.hallazgoId = hallazgoId;
            this.version = version;
        }

        /** Snapshot completo del hallazgo en esta versión. Sin coordenadas: nunca entran al historial. */
        public Builder snapshot(Map<String, Object> valores) {
            this.snapshot = valores == null ? null : new LinkedHashMap<>(valores);
            return this;
        }

        /** Justificación científica del cambio. Obligatoria desde la versión 2. */
        public Builder justificacion(String valor) {
            this.justificacion = valor == null || valor.isBlank() ? null : valor.strip();
            return this;
        }

        /** {@code preferred_username} de quien ejecuta el cambio. */
        public Builder autor(String valor) {
            this.autor = valor == null || valor.isBlank() ? null : valor.strip();
            return this;
        }

        /**
         * Verifica que la versión esté completa y la crea.
         *
         * @throws VersionIncompletaException si falta un obligatorio o si una reclasificación no trae justificación
         */
        public VersionHallazgo build() {
            if (hallazgoId == null) {
                throw new VersionIncompletaException("hallazgoId", "obligatorio");
            }
            if (version < 1) {
                throw new VersionIncompletaException("version", "debe ser mayor o igual a 1");
            }
            if (snapshot == null || snapshot.isEmpty()) {
                throw new VersionIncompletaException("snapshot", "obligatorio");
            }
            if (autor == null) {
                throw new VersionIncompletaException("autor", "obligatorio");
            }
            if (version > 1 && justificacion == null) {
                throw new VersionIncompletaException("justificacion",
                        "la justificacion cientifica es obligatoria al reclasificar");
            }
            return new VersionHallazgo(this);
        }
    }
}
