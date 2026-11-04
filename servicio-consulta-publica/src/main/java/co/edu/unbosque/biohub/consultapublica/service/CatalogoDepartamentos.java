package co.edu.unbosque.biohub.consultapublica.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.consultapublica.domain.Departamento;

/**
 * Catálogo cerrado de 33 departamentos (tabla {@code departamento}). Resuelve un texto que puede ser el código
 * DANE ("15") o el nombre oficial ("Boyacá", "boyaca", "BOYACÁ"). El catálogo es inmutable: se lee de la BD
 * una sola vez con éxito y queda en memoria.
 */
@Component
public class CatalogoDepartamentos {

    private static final Pattern SUFIJO_DC = Pattern.compile("\\s+d\\.?\\s?c\\.?$");

    private final Supplier<List<Departamento>> origen;
    private volatile Map<String, Departamento> indice;

    @Autowired
    public CatalogoDepartamentos(JdbcClient jdbc) {
        this(() -> jdbc.sql("SELECT codigo_dane, nombre, region FROM departamento")
                .query((rs, n) -> new Departamento(rs.getString(1), rs.getString(2), rs.getString(3)))
                .list());
    }

    /** Para pruebas y para cargar el catálogo desde otra fuente. */
    public CatalogoDepartamentos(Supplier<List<Departamento>> origen) {
        this.origen = origen;
    }

    /** Resuelve código o nombre; vacío si no pertenece al catálogo. Puede lanzar un error de acceso a datos. */
    public Optional<Departamento> resolver(String texto) {
        String clave = TextoUtil.normalizar(texto);
        if (clave == null || clave.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(indice().get(clave));
    }

    private Map<String, Departamento> indice() {
        Map<String, Departamento> actual = indice;
        if (actual == null) {
            Map<String, Departamento> nuevo = new HashMap<>();
            for (Departamento d : origen.get()) {
                nuevo.put(TextoUtil.normalizar(d.codigo()), d);
                String nombre = TextoUtil.normalizar(d.nombre());
                nuevo.put(nombre, d);
                // "Bogotá D.C." también se acepta como "Bogotá"
                String sinDc = SUFIJO_DC.matcher(nombre).replaceAll("");
                nuevo.putIfAbsent(sinDc, d);
            }
            if (!nuevo.isEmpty()) {
                indice = nuevo;
            }
            actual = nuevo;
        }
        return actual;
    }
}
