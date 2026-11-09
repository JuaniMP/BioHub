package co.edu.unbosque.biohub.consultapublica.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import co.edu.unbosque.biohub.consultapublica.service.UmbralesDegradacion;

/**
 * Configuración de la consulta pública ({@code biohub.publica.*}).
 *
 * @param modoDegradado FORZADO manual (RNF1, {@code CONSULTA_MODO_DEGRADADO}): si es {@code true} siempre se usa la
 *                      búsqueda degradada, sin importar la carga. Si es {@code false} decide el monitor de carga
 * @param degradacion   umbrales de la degradación automática (ARQ-06, ASR1); todos tienen valor por defecto
 */
@ConfigurationProperties("biohub.publica")
public record PropiedadesPublica(@DefaultValue("false") boolean modoDegradado,
                                 @DefaultValue UmbralesDegradacion degradacion) {
}
