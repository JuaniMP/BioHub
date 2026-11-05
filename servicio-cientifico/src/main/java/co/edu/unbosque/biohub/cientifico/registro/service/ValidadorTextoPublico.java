package co.edu.unbosque.biohub.cientifico.registro.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Impide que una coordenada escrita a mano se cuele en un campo de texto que viaja al portal público
 * (04-contrato-api §7.3, SEC-J-02, RNF9, D14).
 *
 * <p>El riesgo es real: de nada sirve cifrar las coordenadas si alguien escribe "visto en 5.53214,
 * -73.36712" en la descripción. El consumidor de consulta-publica descarta el evento completo si
 * detecta algo así, de modo que registro debe validarlo <b>antes</b> de publicar.
 *
 * <p>Se detectan tres formas: pares de grados decimales, grados con hemisferio y grados-minutos-segundos.
 * Quedan fuera, a propósito, las fechas ({@code 2026-03-14}), las medidas ({@code 12,5 cm}), los rangos
 * de altitud ({@code 3.200, 3.500 msnm}) y las cantidades, porque son texto científico legítimo.
 */
@Component
public class ValidadorTextoPublico {

    /** Par de grados decimales con signo y al menos 3 decimales: "5.53214, -73.36712". */
    private static final Pattern PAR_DECIMAL = Pattern.compile(
            "[-+]?\\d{1,3}\\.\\d{3,}\\s*[,;/ ]\\s*[-+]?\\d{1,3}\\.\\d{3,}");

    /** Grado decimal o entero seguido de hemisferio: "5.53° N", "73 W", "5.53°N". */
    private static final Pattern CON_HEMISFERIO = Pattern.compile(
            "(?i)\\b\\d{1,3}(?:[.,]\\d+)?\\s*(?:°|º|grados?)?\\s*[NSEW]\\b");

    /** Grados-minutos-segundos: "5°31'55\"N", "73° 22' 10\" W". */
    private static final Pattern GRADOS_MIN_SEG = Pattern.compile(
            "(?i)\\d{1,3}\\s*[°º]\\s*\\d{1,2}\\s*['′]\\s*(?:\\d{1,2}(?:[.,]\\d+)?\\s*[\"″]?)?\\s*[NSEW]?");

    /** Altitud y profundidad: no son coordenadas aunque lleven números con separadores. */
    private static final Pattern UNIDAD_NO_GEOGRAFICA = Pattern.compile(
            "(?i)\\b(msnm|m\\.?s\\.?n\\.?m|metros?|m|km|cm|mm|kg|g|ml|l|ha|°c|c|%)\\b");

    /**
     * @return {@code true} si el texto parece contener una coordenada geográfica
     */
    public boolean contieneCoordenadas(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        if (GRADOS_MIN_SEG.matcher(texto).find()) {
            return true;
        }
        if (PAR_DECIMAL.matcher(texto).find()) {
            // "3.200, 3.500 msnm" es un rango de altitud, no un par de coordenadas
            return !UNIDAD_NO_GEOGRAFICA.matcher(texto).find();
        }
        return CON_HEMISFERIO.matcher(texto).find() && !UNIDAD_NO_GEOGRAFICA.matcher(texto).find();
    }

    /**
     * Verifica un campo de texto que acabará en el portal público.
     *
     * @throws DatosHallazgoInvalidosException si el texto parece contener coordenadas
     */
    public void verificar(String campo, String texto) {
        if (contieneCoordenadas(texto)) {
            throw new DatosHallazgoInvalidosException(campo, "no puede contener coordenadas");
        }
    }
}
