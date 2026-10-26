package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del cifrado ({@code biohub.ubicacion.cifrado.*}). Su {@code toString()} no expone las llaves.
 *
 * @param claves          lista "id:base64" separada por comas (UBICACION_CLAVES)
 * @param claveActiva     id de la llave con que se cifra (UBICACION_CLAVE_ACTIVA)
 * @param llaveEfimeraDev solo con perfil "dev": genera una llave efímera si no hay claves
 */
@ConfigurationProperties("biohub.ubicacion.cifrado")
public record PropiedadesCifrado(String claves, String claveActiva, boolean llaveEfimeraDev) {

    @Override
    public String toString() {
        return "PropiedadesCifrado[claves=***, claveActiva=" + claveActiva + "]";
    }
}
