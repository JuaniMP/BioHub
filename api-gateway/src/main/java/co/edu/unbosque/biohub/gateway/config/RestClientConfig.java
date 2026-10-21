package co.edu.unbosque.biohub.gateway.config;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /** RestClient usado por el proxy. No sigue redirecciones: las devuelve tal cual al cliente. */
    @Bean
    RestClient proxyRestClient(RestClient.Builder builder, GatewayProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.timeoutConexion())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.timeoutLectura());
        return builder.requestFactory(factory).build();
    }
}
