package co.edu.unbosque.biohub.cientifico.registro.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un cambio concreto sobre un campo del hallazgo: valor previo, valor nuevo, justificación, autor y
 * fecha (HU-05). Complementa a {@link VersionHallazgo}: la versión guarda el snapshot completo y esta
 * tabla responde "qué cambió exactamente" sin tener que comparar dos JSON.
 *
 * <p>Append-only, igual que el historial: sin setters, y con triggers y permisos que impiden UPDATE y DELETE.
 */
@Entity
@Table(name = "hallazgo_auditoria")
public class CambioAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "hallazgo_id", nullable = false, columnDefinition = "binary(16)")
    private UUID hallazgoId;

    @Column(name = "version_previa")
    private Integer versionPrevia;

    @Column(name = "version_nueva", nullable = false)
    private int versionNueva;

    @Column(name = "campo", nullable = false, length = 60)
    private String campo;

    @Column(name = "valor_previo", length = 1000)
    private String valorPrevio;

    @Column(name = "valor_nuevo", length = 1000)
    private String valorNuevo;

    @Column(name = "justificacion", length = 1000)
    private String justificacion;

    @Column(name = "autor", nullable = false, length = 100)
    private String autor;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private Instant creadoEn;

    protected CambioAuditoria() {
    }

    @SuppressWarnings("java:S107")
    public CambioAuditoria(UUID hallazgoId, Integer versionPrevia, int versionNueva, String campo,
                           String valorPrevio, String valorNuevo, String justificacion, String autor) {
        this.hallazgoId = hallazgoId;
        this.versionPrevia = versionPrevia;
        this.versionNueva = versionNueva;
        this.campo = campo;
        this.valorPrevio = recortar(valorPrevio);
        this.valorNuevo = recortar(valorNuevo);
        this.justificacion = justificacion;
        this.autor = autor;
    }

    /** La columna admite 1.000 caracteres; un valor más largo se recorta para no perder el registro del cambio. */
    private static String recortar(String valor) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= 1000 ? valor : valor.substring(0, 997) + "...";
    }

    public Long getId() {
        return id;
    }

    public UUID getHallazgoId() {
        return hallazgoId;
    }

    public Integer getVersionPrevia() {
        return versionPrevia;
    }

    public int getVersionNueva() {
        return versionNueva;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorPrevio() {
        return valorPrevio;
    }

    public String getValorNuevo() {
        return valorNuevo;
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
}
