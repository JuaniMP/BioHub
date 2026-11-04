package co.edu.unbosque.biohub.consultapublica.controller;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.consultapublica.service.ErrorCampo;
import co.edu.unbosque.biohub.consultapublica.service.ConsultaFichasService;
import co.edu.unbosque.biohub.consultapublica.service.CriterioBusquedaPublica;
import co.edu.unbosque.biohub.consultapublica.service.ParametroInvalidoException;
import co.edu.unbosque.biohub.consultapublica.service.ValidadorBusqueda;
import co.edu.unbosque.biohub.consultapublica.service.vista.FichaPublicaResponse;
import co.edu.unbosque.biohub.consultapublica.service.vista.PaginaFichasResponse;

/**
 * Consulta pública de fichas (contrato 04, §4). Sin autenticación (D16). Nunca devuelve coordenadas (D14).
 * Los parámetros se reciben como texto para validarlos y responder con el formato de error del contrato.
 */
@RestController
@RequestMapping("/api/publica/fichas")
public class FichasPublicasController {

    private static final Pattern UUID_CANONICO =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ValidadorBusqueda validador;
    private final ConsultaFichasService servicio;

    public FichasPublicasController(ValidadorBusqueda validador, ConsultaFichasService servicio) {
        this.validador = validador;
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<PaginaFichasResponse> buscar(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String familia,
                                                       @RequestParam(required = false) String departamento,
                                                       @RequestParam(required = false) String page,
                                                       @RequestParam(required = false) String size) {
        CriterioBusquedaPublica criterio = validador.validar(q, familia, departamento, page, size);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=30")
                .body(servicio.buscar(criterio));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FichaPublicaResponse> ficha(@PathVariable String id) {
        if (id == null || !UUID_CANONICO.matcher(id).matches()) {
            throw new ParametroInvalidoException(List.of(new ErrorCampo("id", "identificador inválido")));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=60")
                .body(servicio.obtener(UUID.fromString(id)));
    }
}
