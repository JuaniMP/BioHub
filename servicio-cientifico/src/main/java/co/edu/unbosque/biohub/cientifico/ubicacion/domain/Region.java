package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Región natural (catálogo de solo lectura). */
@Entity
@Table(name = "ubicacion_region")
public class Region {

    @Id
    @Column(name = "id")
    private Byte id;

    @Column(name = "nombre", nullable = false, length = 40)
    private String nombre;

    protected Region() {
    }

    public Byte getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
