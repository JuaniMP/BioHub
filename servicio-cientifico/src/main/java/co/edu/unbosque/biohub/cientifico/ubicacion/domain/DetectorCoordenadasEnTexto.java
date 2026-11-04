package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

import java.util.regex.Pattern;

/**
 * Detecta coordenadas geográficas escritas dentro de un texto libre (SEC-J-02; D14, RF11, RNF9). El nombre
 * científico entra por aquí y lo ven los 5 roles internos en la búsqueda, y viaja en eventos hacia el portal
 * público: ahí nunca puede haber una ubicación.
 *
 * <p>Equivalente a {@code DetectorCoordenadas} de servicio-consulta-publica (otro despliegue, mismo criterio).
 * Conservador con el texto biológico habitual (fechas, medidas, altitudes con separador de miles). Los
 * cuantificadores están acotados para evitar retrocesos excesivos con texto hostil. No registra ni devuelve el texto.
 */
public final class DetectorCoordenadasEnTexto {

    private static final String SEP = "(?:\\s{0,3}[,;/|]\\s{0,3}|\\s{1,3})";
    private static final String NEG = "[-+−–]";

    private static final Pattern DECIMAL_CON_SIGNO = Pattern.compile(
            "[-+−–]?\\d{1,3}[.,]\\d{3,}" + SEP + NEG + "\\d{1,3}[.,]\\d{3,}");
    private static final Pattern DECIMAL_PRECISO = Pattern.compile(
            "[-+−–]?\\d{1,3}[.,]\\d{4,}" + SEP + "[-+−–]?\\d{1,3}[.,]\\d{4,}");
    private static final Pattern DECIMAL_HEMISFERIO = Pattern.compile(
            "\\d{1,3}(?:[.,]\\d{3,}\\s{0,2}[°º˚]?|[.,]\\d+\\s{0,2}[°º˚]|\\s{0,2}[°º˚])\\s{0,2}[NSEOW]\\b");
    private static final Pattern GRADOS_MINUTOS = Pattern.compile(
            "\\d{1,3}\\s{0,2}[°º˚]\\s{0,2}\\d{1,2}(?:[.,]\\d+)?\\s{0,2}['′’´]");
    private static final Pattern CLAVE_Y_NUMERO = Pattern.compile(
            "\\b(?:lat|latitud|latitude|lng|lon|long|longitude)\\b\\.?\\s{0,2}[:=]?\\s{0,2}" + NEG + "?\\d",
            Pattern.CASE_INSENSITIVE);

    private DetectorCoordenadasEnTexto() {
    }

    /** {@code true} si el texto contiene algo con forma de coordenada (decimal, con hemisferio, grados-minutos o por palabra clave). */
    public static boolean pareceCoordenada(String texto) {
        if (texto == null || texto.isEmpty()) {
            return false;
        }
        return DECIMAL_CON_SIGNO.matcher(texto).find()
                || DECIMAL_PRECISO.matcher(texto).find()
                || DECIMAL_HEMISFERIO.matcher(texto).find()
                || GRADOS_MINUTOS.matcher(texto).find()
                || CLAVE_Y_NUMERO.matcher(texto).find();
    }
}
