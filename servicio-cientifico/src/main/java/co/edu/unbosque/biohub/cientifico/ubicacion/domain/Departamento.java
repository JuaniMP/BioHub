package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Departamento de Colombia (catálogo de solo lectura, código DANE de 2 dígitos). */
@Entity
@Table(name = "ubicacion_departamento")
public class Departamento {

    @Id
    @Column(name = "codigo_dane", length = 2)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    protected Departamento() {
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public Region getRegion() {
        return region;
    }
}
