package co.edu.unbosque.biohub.consultapublica.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import co.edu.unbosque.biohub.consultapublica.service.MonitorCarga;
import co.edu.unbosque.biohub.consultapublica.service.UmbralesDegradacion;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Degradación dinámica (ARQ-06, ASR1): propiedades, filtro que mide la carga y estado en actuator.
 * El estado se ve en {@code /actuator/info} (clave {@code busqueda}) y en las métricas
 * {@code biohub.consulta.degradado}, {@code biohub.consulta.en_curso} y {@code biohub.consulta.p95.ms}.
 * No cambia el estado de salud: estar degradado es una respuesta correcta a la carga, no una falla.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesPublica.class)
public class ConfiguracionDegradacion {

    /** Entrega a {@link MonitorCarga} solo los umbrales, sin que el servicio conozca {@code config} (ARQ-28). */
    @Bean
    UmbralesDegradacion umbralesDegradacion(PropiedadesPublica propiedades) {
        return propiedades.degradacion();
    }

    @Bean
    FilterRegistrationBean<MonitorCargaFilter> monitorCargaFilter(MonitorCarga monitor) {
        FilterRegistrationBean<MonitorCargaFilter> registro = new FilterRegistrationBean<>(new MonitorCargaFilter(monitor));
        registro.addUrlPatterns("/api/publica/fichas", "/api/publica/fichas/*");
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); // antes que la seguridad: mide la petición completa
        return registro;
    }

    @Bean
    InfoContributor busquedaInfoContributor(MonitorCarga monitor) {
        return (Info.Builder builder) -> {
            MonitorCarga.Estado e = monitor.estado();
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("modo", e.degradado() ? "degradada" : "completa");
            datos.put("origen", e.origen().name().toLowerCase(java.util.Locale.ROOT));
            datos.put("enCurso", e.enCurso());
            datos.put("p95Ms", e.p95Ms());
            datos.put("muestras", e.muestras());
            datos.put("desde", e.desde().toString());
            datos.put("cambios", e.cambios());
            builder.withDetail("busqueda", datos);
        };
    }

    @Bean
    MeterBinder monitorCargaMetricas(MonitorCarga monitor) {
        return registro -> {
            Gauge.builder("biohub.consulta.degradado", monitor, m -> m.estado().degradado() ? 1 : 0)
                    .description("1 si la búsqueda pública está en modo degradado").register(registro);
            Gauge.builder("biohub.consulta.en_curso", monitor, m -> m.estado().enCurso())
                    .description("Lecturas públicas en curso").register(registro);
            Gauge.builder("biohub.consulta.p95.ms", monitor, m -> m.estado().p95Ms())
                    .description("p95 de la duración de las lecturas públicas en la ventana").register(registro);
        };
    }
}
