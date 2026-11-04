package co.edu.unbosque.biohub.consultapublica.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.consultapublica.service.ErrorCampo;
import co.edu.unbosque.biohub.consultapublica.domain.Departamento;

/**
 * Valida y normaliza los parámetros de {@code GET /api/publica/fichas} (contrato 04, §4.1):
 * recorta espacios, el texto vacío equivale a ausente y reúne todos los errores de campo. Valida SIEMPRE todo
 * lo enviado (también lo que el modo degradado luego ignora) y delega en la {@link EstrategiaBusqueda} vigente
 * el armado del criterio.
 */
@Component
public class ValidadorBusqueda {

    static final int PAGE_POR_DEFECTO = 0;
    static final int SIZE_POR_DEFECTO = 20;
    static final int SIZE_MAXIMO = 50;
    static final long MAXIMO_RESULTADOS_PAGINABLES = 10_000;

    private final CatalogoDepartamentos catalogo;
    private final ElectorEstrategiaBusqueda elector;

    public ValidadorBusqueda(CatalogoDepartamentos catalogo, ElectorEstrategiaBusqueda elector) {
        this.catalogo = catalogo;
        this.elector = elector;
    }

    public CriterioBusquedaPublica validar(String qCrudo, String familiaCruda, String departamentoCrudo,
                                           String pageCrudo, String sizeCrudo) {
        List<ErrorCampo> errores = new ArrayList<>();

        String q = TextoUtil.recortarOCero(qCrudo);
        if (q != null && (q.length() < 2 || q.length() > 100)) {
            errores.add(new ErrorCampo("q", "debe tener entre 2 y 100 caracteres"));
        }

        String familia = TextoUtil.recortarOCero(familiaCruda);
        if (familia != null && familia.length() > 100) {
            errores.add(new ErrorCampo("familia", "máximo 100 caracteres"));
        }

        String departamentoTexto = TextoUtil.recortarOCero(departamentoCrudo);
        String departamentoCodigo = null;
        if (departamentoTexto != null) {
            Optional<Departamento> d = departamentoTexto.length() > 60 ? Optional.empty()
                    : catalogo.resolver(departamentoTexto);
            if (d.isPresent()) {
                departamentoCodigo = d.get().codigo();
            } else {
                errores.add(new ErrorCampo("departamento", "departamento desconocido"));
            }
        }

        Integer page = entero(TextoUtil.recortarOCero(pageCrudo), PAGE_POR_DEFECTO);
        if (page == null || page < 0) {
            errores.add(new ErrorCampo("page", "debe ser un entero mayor o igual a 0"));
            page = null;
        }
        Integer size = entero(TextoUtil.recortarOCero(sizeCrudo), SIZE_POR_DEFECTO);
        if (size == null || size < 1 || size > SIZE_MAXIMO) {
            errores.add(new ErrorCampo("size", "debe estar entre 1 y 50"));
            size = null;
        }
        if (page != null && size != null && ((long) page + 1) * size > MAXIMO_RESULTADOS_PAGINABLES) {
            errores.add(new ErrorCampo("page", "no se puede paginar más allá de 10.000 resultados"));
        }

        if (!errores.isEmpty()) {
            throw new ParametroInvalidoException(errores);
        }

        // Strategy (ARQ-06): la búsqueda completa o la degradada se elige en cada petición según la carga
        return elector.actual().criterio(q, familia, departamentoCodigo, page, size);
    }

    /** Entero decimal sin signo "+" ni espacios internos; {@code null} si no es un entero válido de 32 bits. */
    private static Integer entero(String texto, int porDefecto) {
        if (texto == null) {
            return porDefecto;
        }
        try {
            return Integer.valueOf(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
