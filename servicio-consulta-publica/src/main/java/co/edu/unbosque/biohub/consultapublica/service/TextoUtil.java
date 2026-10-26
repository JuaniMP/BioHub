package co.edu.unbosque.biohub.consultapublica.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalización de texto para comparar sin distinguir mayúsculas ni tildes. */
public final class TextoUtil {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");

    private TextoUtil() {
    }

    /** Minúsculas, sin tildes y sin espacios en los extremos. {@code null} se mantiene {@code null}. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String sinMarcas = MARCAS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return sinMarcas.toLowerCase(Locale.ROOT).trim();
    }

    /** Recorta; el texto vacío equivale a ausente ({@code null}). */
    public static String recortarOCero(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.strip();
        return t.isEmpty() ? null : t;
    }
}
