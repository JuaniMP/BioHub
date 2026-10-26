package co.edu.unbosque.biohub.cientifico.ubicacion;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import co.edu.unbosque.biohub.cientifico.config.SecurityConfig;

/**
 * Aplicación mínima de pruebas: solo el módulo ubicacion + la seguridad compartida. No carga RabbitMQ
 * ni el módulo registro, para que las pruebas no dependan del trabajo de otros.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = RabbitAutoConfiguration.class)
@ComponentScan(basePackages = {
        "co.edu.unbosque.biohub.cientifico.ubicacion.service", "co.edu.unbosque.biohub.cientifico.ubicacion.controller", "co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure"})
@EntityScan("co.edu.unbosque.biohub.cientifico.ubicacion.domain")
@EnableJpaRepositories("co.edu.unbosque.biohub.cientifico.ubicacion.domain")
@Import(SecurityConfig.class)
public class AppPruebaUbicacion {
}
