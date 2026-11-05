package co.edu.unbosque.biohub.cientifico.registro;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import co.edu.unbosque.biohub.cientifico.config.SecurityConfig;

/**
 * Aplicación mínima para las pruebas del módulo registro: carga registro y ubicación, porque el alta de
 * un hallazgo cruza a ubicación por el evento de dominio síncrono y esa colaboración es justo lo que
 * hay que probar. RabbitMQ queda fuera: el envío al broker ocurre después del commit y se prueba aparte.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = RabbitAutoConfiguration.class)
@ComponentScan(basePackages = {
        "co.edu.unbosque.biohub.cientifico.registro.service",
        "co.edu.unbosque.biohub.cientifico.registro.controller",
        "co.edu.unbosque.biohub.cientifico.ubicacion.service",
        "co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure"})
@EntityScan({"co.edu.unbosque.biohub.cientifico.registro.domain",
        "co.edu.unbosque.biohub.cientifico.ubicacion.domain"})
@EnableJpaRepositories({"co.edu.unbosque.biohub.cientifico.registro.domain",
        "co.edu.unbosque.biohub.cientifico.ubicacion.domain"})
@Import(SecurityConfig.class)
public class AppPruebaRegistro {
}
