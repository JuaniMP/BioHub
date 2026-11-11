package co.edu.unbosque.biohub.gateway.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Universidad u ONG autorizada a usar la API externa (HU-12). El límite es por consumidor (RNF11). */
@Entity
@Table(name = "consumidor_externo")
public class ConsumidorExterno {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", columnDefinition = "binary(16)")
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "contacto_email", nullable = false, length = 254)
    private String contactoEmail;

    /** null = usa el límite por defecto. */
    @Column(name = "limite_por_minuto")
    private Integer limitePorMinuto;

    @Column(name = "creado_por", nullable = false, updatable = false, length = 64)
    private String creadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected ConsumidorExterno() {
    }

    public ConsumidorExterno(UUID id, String nombre, String contactoEmail, Integer limitePorMinuto,
                             String creadoPor, Instant ahora) {
        this.id = id;
        this.nombre = nombre;
        this.contactoEmail = contactoEmail;
        this.limitePorMinuto = limitePorMinuto;
        this.creadoPor = creadoPor;
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    public void cambiarLimite(Integer limite, Instant ahora) {
        this.limitePorMinuto = limite;
        this.actualizadoEn = ahora;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getContactoEmail() {
        return contactoEmail;
    }

    public Integer getLimitePorMinuto() {
        return limitePorMinuto;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
