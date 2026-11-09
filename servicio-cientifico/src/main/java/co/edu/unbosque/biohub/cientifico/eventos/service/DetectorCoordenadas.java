package co.edu.unbosque.biohub.cientifico.eventos.service;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detecta coordenadas geográficas en la carga útil de un evento {@code hallazgo.*} ANTES de publicarlo, tanto por
 * el NOMBRE de una clave como por el TEXTO libre de un valor (SEC-J-13, D54; D14: el portal público nunca lleva
 * coordenadas).
 *
 * <p>Adaptación de {@code DetectorCoordenadas} de servicio-consulta-publica (otro despliegue, mismo criterio; no hay
 * dependencia entre servicios). Vive en {@code eventos.service} y no reutiliza el de {@code ubicacion.domain} para
 * que el módulo de eventos no dependa de ubicacion.
 *
 * <p>Es una defensa en profundidad: la fuente (registro) debe impedir que se escriban. Tiene que ser conservadora
 * con los falsos negativos pero no bloquear texto biológico habitual: fechas ("2026-03-14"), medidas
 * ("12,5 cm"), rangos de altitud ("3.200, 3.500 msnm") y cantidades no se consideran coordenadas.
 *
 * <p>Todos los cuantificadores de los patrones están acotados para que un texto hostil no provoque
 * retrocesos excesivos. Esta clase nunca devuelve ni registra el texto analizado.
 */
final class DetectorCoordenadas {

    /** Claves prohibidas en minúsculas (D14, contrato 04 §7.3). */
    static final Set<String> CLAVES_PROHIBIDAS = Set.of("latitud", "longitud", "lat", "lon", "lng", "latitude",
            "longitude", "coordenadas", "coordinates", "geometry", "geom", "ubicacionexacta");

    private static final String SEP = "(?:\\s{0,3}[,;/|]\\s{0,3}|\\s{1,3})";
    private static final String NEG = "[-+−–]";

    /** Par decimal: el segundo con signo (la longitud de Colombia es negativa) o ambos con 4 o más decimales. */
    private static final Pattern DECIMAL_CON_SIGNO = Pattern.compile(
            "[-+−–]?\\d{1,3}[.,]\\d{3,}" + SEP + NEG + "\\d{1,3}[.,]\\d{3,}");
    private static final Pattern DECIMAL_PRECISO = Pattern.compile(
            "[-+−–]?\\d{1,3}[.,]\\d{4,}" + SEP + "[-+−–]?\\d{1,3}[.,]\\d{4,}");
    /** Grados decimales con hemisferio en mayúscula (así evita "1.500 o 2.000"): "5.53214 N", "73.3671°W", "5,5°N". */
    private static final Pattern DECIMAL_HEMISFERIO = Pattern.compile(
            "\\d{1,3}(?:[.,]\\d{3,}\\s{0,2}[°º˚]?|[.,]\\d+\\s{0,2}[°º˚]|\\s{0,2}[°º˚])\\s{0,2}[NSEOW]\\b");
    /** Grados y minutos: 5°31'55"N, 5º 31.9' , 73° 22′. */
    private static final Pattern GRADOS_MINUTOS = Pattern.compile(
            "\\d{1,3}\\s{0,2}[°º˚]\\s{0,2}\\d{1,2}(?:[.,]\\d+)?\\s{0,2}['′’´]");
    /** Palabra clave de coordenada seguida de un número: "lat: 5.5", "lng=-73". ("longitud" también significa largo). */
    private static final Pattern CLAVE_Y_NUMERO = Pattern.compile(
            "\\b(?:lat|latitud|latitude|lng|lon|long|longitude)\\b\\.?\\s{0,2}[:=]?\\s{0,2}" + NEG + "?\\d",
            Pattern.CASE_INSENSITIVE);

    private DetectorCoordenadas() {
    }

    /** {@code true} si el texto contiene algo con forma de coordenada (decimal, con hemisferio, grados-minutos o por palabra clave). */
    static boolean pareceCoordenada(String texto) {
        if (texto == null || texto.isEmpty()) {
            return false;
        }
        return DECIMAL_CON_SIGNO.matcher(texto).find()
                || DECIMAL_PRECISO.matcher(texto).find()
                || DECIMAL_HEMISFERIO.matcher(texto).find()
                || GRADOS_MINUTOS.matcher(texto).find()
                || CLAVE_Y_NUMERO.matcher(texto).find();
    }

    /** {@code true} si la clave es de coordenadas (sin distinguir mayúsculas). */
    static boolean esClaveProhibida(Object clave) {
        return clave != null && CLAVES_PROHIBIDAS.contains(clave.toString().toLowerCase(Locale.ROOT));
    }

    /**
     * Recorre mapas, listas y cadenas en cualquier profundidad: {@code true} si alguna CLAVE es de coordenadas o
     * algún VALOR de texto parece una coordenada.
     */
    static boolean contiene(Object nodo) {
        return contiene(nodo, 0);
    }

    private static boolean contiene(Object nodo, int profundidad) {
        if (profundidad > 64) { // una estructura tan honda no es un evento legítimo: se trata como sospechosa
            return true;
        }
        if (nodo instanceof Map<?, ?> mapa) {
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (esClaveProhibida(e.getKey()) || (e.getKey() instanceof String k && pareceCoordenada(k))
                        || contiene(e.getValue(), profundidad + 1)) {
                    return true;
                }
            }
        } else if (nodo instanceof Collection<?> coleccion) {
            for (Object o : coleccion) {
                if (contiene(o, profundidad + 1)) {
                    return true;
                }
            }
        } else if (nodo instanceof String s) {
            return pareceCoordenada(s);
        }
        return false;
    }
}
