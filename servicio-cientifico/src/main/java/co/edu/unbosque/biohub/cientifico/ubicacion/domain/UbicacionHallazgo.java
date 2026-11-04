package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.EstadoConservacion;

/**
 * Índice de búsqueda interno + coordenadas CIFRADAS de un hallazgo. No existen columnas de
 * latitud/longitud en claro. {@code version} es la del hallazgo (de registro), no un @Version de JPA.
 */
@Entity
@DynamicUpdate
@Table(name = "ubicacion_hallazgo")
public class UbicacionHallazgo {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "hallazgo_id", columnDefinition = "binary(16)")
    private UUID id;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "departamento_codigo", nullable = false, length = 2)
    private String departamentoCodigo;

    @Column(name = "nombre_cientifico", nullable = false, length = 150)
    private String nombreCientifico;

    @Column(name = "fecha_recoleccion", nullable = false)
    private LocalDate fechaRecoleccion;

    @Column(name = "investigador", nullable = false, length = 100)
    private String investigador;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conservacion", nullable = false, length = 20)
    private EstadoConservacion estadoConservacion;

    @Column(name = "coord_cifrada", length = 64)
    private byte[] coordCifrada;

    @Column(name = "coord_nonce", length = 12)
    private byte[] coordNonce;

    @Column(name = "clave_version", length = 16)
    private String claveVersion;

    @Column(name = "cifrado_en")
    private Instant cifradoEn;

    @Column(name = "eliminado_en")
    private Instant eliminadoEn;

    protected UbicacionHallazgo() {
    }

    public UbicacionHallazgo(UUID id, int version, String departamentoCodigo, String nombreCientifico,
                             LocalDate fechaRecoleccion, String investigador, EstadoConservacion estadoConservacion) {
        this.id = id;
        this.version = version;
        this.departamentoCodigo = departamentoCodigo;
        this.nombreCientifico = nombreCientifico;
        this.fechaRecoleccion = fechaRecoleccion;
        this.investigador = investigador;
        this.estadoConservacion = estadoConservacion;
    }

    /** Actualiza los campos indexados (no toca coordenadas ni investigador). */
    public void actualizarIndice(int version, String departamentoCodigo, String nombreCientifico,
                                 LocalDate fechaRecoleccion, EstadoConservacion estadoConservacion) {
        this.version = version;
        this.departamentoCodigo = departamentoCodigo;
        this.nombreCientifico = nombreCientifico;
        this.fechaRecoleccion = fechaRecoleccion;
        this.estadoConservacion = estadoConservacion;
    }

    /** Guarda el cifrado (alta o re-cifrado) en las 4 columnas, que van juntas. */
    public void asignarCifrado(CoordenadasCifradas cifradas, Instant cifradoEn) {
        this.coordCifrada = cifradas.textoCifrado();
        this.coordNonce = cifradas.nonce();
        this.claveVersion = cifradas.claveVersion();
        this.cifradoEn = cifradoEn;
    }

    public boolean tieneCoordenadas() {
        return coordCifrada != null;
    }

    public CoordenadasCifradas coordenadasCifradas() {
        return coordCifrada == null ? null : new CoordenadasCifradas(coordCifrada, coordNonce, claveVersion);
    }

    public UUID getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public String getDepartamentoCodigo() {
        return departamentoCodigo;
    }

    public String getNombreCientifico() {
        return nombreCientifico;
    }

    public LocalDate getFechaRecoleccion() {
        return fechaRecoleccion;
    }

    public String getInvestigador() {
        return investigador;
    }

    public EstadoConservacion getEstadoConservacion() {
        return estadoConservacion;
    }

    public String getClaveVersion() {
        return claveVersion;
    }

    public Instant getCifradoEn() {
        return cifradoEn;
    }

    public Instant getEliminadoEn() {
        return eliminadoEn;
    }

    @Override
    public String toString() {
        return "UbicacionHallazgo[id=" + id + ", version=" + version + ", coordenadas=***]";
    }
}
