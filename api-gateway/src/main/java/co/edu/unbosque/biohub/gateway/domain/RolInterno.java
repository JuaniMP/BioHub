package co.edu.unbosque.biohub.gateway.domain;

import java.util.Arrays;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Catálogo cerrado de roles internos (RF17). Son los roles de realm de Keycloak. */
public enum RolInterno {
    INVESTIGADOR_CAMPO("investigador_campo"),
    AUDITOR_CIENTIFICO("auditor_cientifico"),
    CIENTIFICO_ALTO_RANGO("cientifico_alto_rango"),
    ANALISTA_AMBIENTAL("analista_ambiental"),
    ADMINISTRADOR("administrador");

    private final String nombre;

    RolInterno(String nombre) {
        this.nombre = nombre;
    }

    @JsonValue
    public String nombre() {
        return nombre;
    }

    public static Optional<RolInterno> buscar(String nombre) {
        return Arrays.stream(values()).filter(rol -> rol.nombre.equals(nombre)).findFirst();
    }

    /** Un nombre fuera del catálogo hace fallar la lectura del JSON (400). */
    @JsonCreator
    public static RolInterno desdeJson(String nombre) {
        return buscar(nombre).orElseThrow(() -> new IllegalArgumentException("Rol desconocido"));
    }

    public static String[] nombres() {
        return Arrays.stream(values()).map(RolInterno::nombre).toArray(String[]::new);
    }
}
