package co.edu.unbosque.biohub.gateway.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Piezas generales del gateway: reloj inyectable y tareas programadas. */
@Configuration
@EnableScheduling
public class ConfiguracionGateway {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
