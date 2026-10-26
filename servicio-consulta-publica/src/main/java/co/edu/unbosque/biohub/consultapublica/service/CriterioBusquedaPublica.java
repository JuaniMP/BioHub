package co.edu.unbosque.biohub.consultapublica.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Criterio ya validado de la búsqueda pública. Arma la expresión FULLTEXT (D26) y la clave de caché.
 *
 * @param q                texto de búsqueda recortado, o {@code null}
 * @param familia          familia (recortada), o {@code null}; en modo degradado siempre {@code null}
 * @param departamentoCodigo código DANE ya resuelto, o {@code null}; en modo degradado siempre {@code null}
 * @param page             base 0
 * @param filtrosIgnorados filtros enviados que el modo degradado no aplicó
 */
public record CriterioBusquedaPublica(String q, String familia, String departamentoCodigo, int page, int size,
                                      boolean modoDegradado, List<String> filtrosIgnorados) {

    /** InnoDB no indexa palabras de menos de 3 caracteres (innodb_ft_min_token_size). */
    static final int LARGO_MINIMO_PALABRA = 3;
    private static final Pattern SEPARADORES = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Palabras de {@code q} aptas para FULLTEXT: se descartan operadores y signos, y las de menos de 3 letras. */
    public List<String> palabrasFulltext() {
        if (q == null) {
            return List.of();
        }
        return Arrays.stream(SEPARADORES.split(q))
                .filter(p -> p.length() >= LARGO_MINIMO_PALABRA)
                .toList();
    }

    /** {@code +pal1* +pal2*} para {@code MATCH ... AGAINST (? IN BOOLEAN MODE)}; vacío si no hay palabras aptas. */
    public Optional<String> expresionFulltext() {
        List<String> palabras = palabrasFulltext();
        if (palabras.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join(" ", palabras.stream().map(p -> "+" + p + "*").toList()));
    }

    /** Patrón {@code LIKE} por prefijo con {@code \}, {@code %} y {@code _} escapados (q sin palabras aptas). */
    public String patronPrefijoLike() {
        String escapado = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return escapado + "%";
    }

    public long offset() {
        return (long) page * size;
    }

    /**
     * Clave de la caché {@code busquedas}: q y familia en minúsculas y sin tildes. Cada componente va codificado
     * para que un {@code q} con "|" o "=" no pueda colisionar con otra combinación de filtros.
     */
    public String claveCache() {
        return "q=" + codificar(TextoUtil.normalizar(q))
                + "|f=" + codificar(TextoUtil.normalizar(familia))
                + "|d=" + codificar(departamentoCodigo)
                + "|p=" + page
                + "|s=" + size
                + "|deg=" + modoDegradado
                + (modoDegradado ? "|ign=" + String.join(",", filtrosIgnorados) : "");
    }

    private static String codificar(String valor) {
        return valor == null ? "" : URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
