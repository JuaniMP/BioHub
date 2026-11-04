package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Departamento;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.DepartamentoRepository;

/** Resuelve un departamento por código DANE o nombre oficial (sin distinguir mayúsculas ni tildes). */
@Component
public class CatalogoDepartamentos {

    private final DepartamentoRepository repositorio;
    private volatile Map<String, Departamento> indice;

    public CatalogoDepartamentos(DepartamentoRepository repositorio) {
        this.repositorio = repositorio;
    }

    public Optional<Departamento> resolver(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(indice().get(normalizar(texto)));
    }

    private Map<String, Departamento> indice() {
        Map<String, Departamento> actual = indice;
        if (actual == null) {
            synchronized (this) {
                actual = indice;
                if (actual == null) {
                    Map<String, Departamento> nuevo = new HashMap<>();
                    for (Departamento d : repositorio.findAll()) {
                        nuevo.put(normalizar(d.getCodigo()), d);
                        nuevo.put(normalizar(d.getNombre()), d);
                        if ("11".equals(d.getCodigo())) {
                            nuevo.put("bogota", d);
                        }
                    }
                    // El catálogo es de referencia e inmutable; si vino vacío no se cachea
                    if (!nuevo.isEmpty()) {
                        indice = nuevo;
                    }
                    actual = nuevo;
                }
            }
        }
        return actual;
    }

    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
