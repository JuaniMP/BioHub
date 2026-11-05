package co.edu.unbosque.biohub.cientifico.registro.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Plantilla de ficha taxonómica de una especie (HU-02): el conjunto de atributos personalizados que se
 * le piden al investigador al registrar un hallazgo de esa especie.
 *
 * <p>Cada cambio crea una <b>versión nueva</b> en vez de modificar la anterior. Así, los hallazgos ya
 * guardados conservan la versión de plantilla con la que se capturaron y no se corrompen cuando el
 * equipo agrega o retira campos (criterio de aceptación 3 de HU-02).
 *
 * <p>Los atributos viven en una columna JSON, de modo que agregar un campo no exige migración ni
 * desarrollo (ASR5).
 */
@Entity
@Table(name = "ficha_plantilla")
public class FichaPlantilla {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "binary(16)")
    private UUID id;

    @Column(name = "nombre_cientifico", nullable = false, length = 150)
    private String nombreCientifico;

    @Column(name = "version", nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "atributos", nullable = false)
    private List<AtributoPlantilla> atributos;

    @Column(name = "creado_por", nullable = false, length = 100)
    private String creadoPor;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private Instant creadoEn;

    protected FichaPlantilla() {
    }

    public FichaPlantilla(String nombreCientifico, int version, List<AtributoPlantilla> atributos, String creadoPor) {
        this.id = UUID.randomUUID();
        this.nombreCientifico = nombreCientifico;
        this.version = version;
        this.atributos = List.copyOf(atributos);
        this.creadoPor = creadoPor;
    }

    public UUID getId() {
        return id;
    }

    public String getNombreCientifico() {
        return nombreCientifico;
    }

    public int getVersion() {
        return version;
    }

    public List<AtributoPlantilla> getAtributos() {
        return atributos == null ? List.of() : List.copyOf(atributos);
    }

    /** Solo los atributos vigentes: son los que el formulario debe pedir y validar. */
    public List<AtributoPlantilla> getAtributosHabilitados() {
        return getAtributos().stream().filter(AtributoPlantilla::habilitado).toList();
    }

    public String getCreadoPor() {
        return creadoPor;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
