package co.edu.unbosque.biohub.cientifico.registro.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import co.edu.unbosque.biohub.cientifico.ubicacion.service.EstadoConservacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Estado ACTUAL de un hallazgo. El historial completo de cómo llegó hasta aquí vive en
 * {@link VersionHallazgo}, que es append-only (ASR7).
 *
 * <p>No tiene columnas de latitud ni longitud: las coordenadas se entregan a ubicación, que las
 * guarda cifradas, y nunca se persisten ni se registran en este módulo (D14, RNF9).
 *
 * <p>{@code version} es la versión del hallazgo que viaja a {@code UbicacionService} y al evento
 * público; crece estrictamente con cada cambio. No es un {@code @Version} de JPA.
 */
@Entity
@DynamicUpdate
@Table(name = "hallazgo")
public class Hallazgo {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "binary(16)")
    private UUID id;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "nombre_cientifico", nullable = false, length = 150)
    private String nombreCientifico;

    @Column(name = "nombre_comun", length = 150)
    private String nombreComun;

    @Column(name = "familia", nullable = false, length = 100)
    private String familia;

    @Column(name = "tipo_especimen", length = 80)
    private String tipoEspecimen;

    @Column(name = "fecha_recoleccion", nullable = false)
    private LocalDate fechaRecoleccion;

    @Column(name = "investigador", nullable = false, length = 100)
    private String investigador;

    @Column(name = "departamento", nullable = false, length = 60)
    private String departamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conservacion", nullable = false, length = 20)
    private EstadoConservacion estadoConservacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria_uicn", length = 2)
    private CategoriaUicn categoriaUicn;

    @Column(name = "descripcion", length = 5000)
    private String descripcion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "atributos")
    private Map<String, Object> atributos;

    @Column(name = "plantilla_version")
    private Integer plantillaVersion;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    protected Hallazgo() {
    }

    @SuppressWarnings("java:S107") // un hallazgo tiene muchos datos obligatorios; se construye desde un comando validado
    public Hallazgo(UUID id, String nombreCientifico, String nombreComun, String familia, String tipoEspecimen,
                    LocalDate fechaRecoleccion, String investigador, String departamento,
                    EstadoConservacion estadoConservacion, CategoriaUicn categoriaUicn, String descripcion,
                    Map<String, Object> atributos, Integer plantillaVersion) {
        this.id = id;
        this.version = 1;
        this.nombreCientifico = nombreCientifico;
        this.nombreComun = nombreComun;
        this.familia = familia;
        this.tipoEspecimen = tipoEspecimen;
        this.fechaRecoleccion = fechaRecoleccion;
        this.investigador = investigador;
        this.departamento = departamento;
        this.estadoConservacion = estadoConservacion;
        this.categoriaUicn = categoriaUicn;
        this.descripcion = descripcion;
        this.atributos = atributos == null ? null : new LinkedHashMap<>(atributos);
        this.plantillaVersion = plantillaVersion;
    }

    /**
     * Aplica una reclasificación (HU-03) y devuelve la versión nueva. Solo cambia los campos no nulos
     * del comando; el investigador y la fecha de creación nunca se tocan.
     *
     * @return la versión resultante, que es la anterior + 1
     */
    @SuppressWarnings("java:S107")
    public int reclasificar(String nombreCientifico, String nombreComun, String familia, String tipoEspecimen,
                            LocalDate fechaRecoleccion, String departamento, EstadoConservacion estadoConservacion,
                            CategoriaUicn categoriaUicn, String descripcion, Map<String, Object> atributos) {
        if (nombreCientifico != null) {
            this.nombreCientifico = nombreCientifico;
        }
        if (nombreComun != null) {
            this.nombreComun = nombreComun;
        }
        if (familia != null) {
            this.familia = familia;
        }
        if (tipoEspecimen != null) {
            this.tipoEspecimen = tipoEspecimen;
        }
        if (fechaRecoleccion != null) {
            this.fechaRecoleccion = fechaRecoleccion;
        }
        if (departamento != null) {
            this.departamento = departamento;
        }
        if (estadoConservacion != null) {
            this.estadoConservacion = estadoConservacion;
            this.categoriaUicn = categoriaUicn;
        }
        if (descripcion != null) {
            this.descripcion = descripcion;
        }
        if (atributos != null) {
            this.atributos = new LinkedHashMap<>(atributos);
        }
        this.version = this.version + 1;
        return this.version;
    }

    /** Marca el hallazgo como anulado. No se borra: su historial debe permanecer intacto (doc 05 §6.4). */
    public void anular(Instant momento) {
        this.anuladoEn = momento;
    }

    public boolean estaAnulado() {
        return anuladoEn != null;
    }

    public UUID getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public String getNombreCientifico() {
        return nombreCientifico;
    }

    public String getNombreComun() {
        return nombreComun;
    }

    public String getFamilia() {
        return familia;
    }

    public String getTipoEspecimen() {
        return tipoEspecimen;
    }

    public LocalDate getFechaRecoleccion() {
        return fechaRecoleccion;
    }

    public String getInvestigador() {
        return investigador;
    }

    public String getDepartamento() {
        return departamento;
    }

    public EstadoConservacion getEstadoConservacion() {
        return estadoConservacion;
    }

    public CategoriaUicn getCategoriaUicn() {
        return categoriaUicn;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Valores de los atributos personalizados; nunca {@code null} para quien lee. */
    public Map<String, Object> getAtributos() {
        return atributos == null ? Map.of() : Map.copyOf(atributos);
    }

    public Integer getPlantillaVersion() {
        return plantillaVersion;
    }

    public Instant getAnuladoEn() {
        return anuladoEn;
    }

    @Override
    public String toString() {
        return "Hallazgo[id=" + id + ", version=" + version + "]";
    }
}
