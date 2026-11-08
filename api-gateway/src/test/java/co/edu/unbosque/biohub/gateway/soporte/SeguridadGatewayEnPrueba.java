package co.edu.unbosque.biohub.gateway.soporte;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import co.edu.unbosque.biohub.gateway.config.GatewayProperties;
import co.edu.unbosque.biohub.gateway.config.PropiedadesLimites;
import co.edu.unbosque.biohub.gateway.config.RutasGateway;
import co.edu.unbosque.biohub.gateway.config.SecurityConfig;
import co.edu.unbosque.biohub.gateway.infrastructure.filtro.ApiKeyRateLimitFilter;
import co.edu.unbosque.biohub.gateway.infrastructure.filtro.EscritorProblema;
import co.edu.unbosque.biohub.gateway.infrastructure.filtro.RevocacionTokenFilter;

/** Carga la cadena de seguridad real en un @WebMvcTest, con límites pequeños y fáciles de razonar. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({SecurityConfig.class, RutasGateway.class, EscritorProblema.class, ApiKeyRateLimitFilter.class,
        RevocacionTokenFilter.class})
@EnableConfigurationProperties({GatewayProperties.class, PropiedadesLimites.class})
@TestPropertySource(properties = {
        "biohub.gateway.rutas.cientifico=http://cientifico",
        "biohub.gateway.rutas.equipos=http://equipos",
        "biohub.gateway.rutas.publica=http://publica",
        "biohub.gateway.rutas.reportes=http://reportes",
        "biohub.gateway.rutas-publicas=publica",
        "biohub.gateway.origenes-permitidos=http://localhost:5173",
        "biohub.gateway.limites.por-consumidor=60",
        "biohub.gateway.limites.por-ip-portal=120",
        "biohub.gateway.limites.fallos-key-por-ip=20",
        "biohub.gateway.limites.ventana=60s"
})
public @interface SeguridadGatewayEnPrueba {
}
