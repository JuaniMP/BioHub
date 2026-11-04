package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Departamento;

/** Valida y normaliza parámetros de consulta (recorta espacios; vacío = ausente) acumulando los errores. */
final class ValidadorConsulta {

    static final int LIMITE_RESULTADOS = 10_000;
    private static final Pattern FECHA = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final List<ErrorCampo> errores = new ArrayList<>();
    private final CatalogoDepartamentos catalogo;

    ValidadorConsulta(CatalogoDepartamentos catalogo) {
        this.catalogo = catalogo;
    }

    static String recortar(String valor) {
        if (valor == null) {
            return null;
        }
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }

    String texto(String campo, String valor, int min, int max, String mensaje) {
        String t = recortar(valor);
        if (t != null && (t.length() < min || t.length() > max)) {
            errores.add(new ErrorCampo(campo, mensaje));
            return null;
        }
        return t;
    }

    LocalDate fecha(String campo, String valor) {
        String t = recortar(valor);
        if (t == null) {
            return null;
        }
        try {
            if (!FECHA.matcher(t).matches()) {
                throw new DateTimeParseException("fecha", t, 0);
            }
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            errores.add(new ErrorCampo(campo, "fecha inválida, formato AAAA-MM-DD"));
            return null;
        }
    }

    void rangoFechas(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            errores.add(new ErrorCampo("hasta", "debe ser igual o posterior a 'desde'"));
        }
    }

    String departamento(String valor) {
        String t = recortar(valor);
        if (t == null) {
            return null;
        }
        Optional<Departamento> d = catalogo.resolver(t);
        if (d.isEmpty()) {
            errores.add(new ErrorCampo("departamento", "departamento desconocido"));
            return null;
        }
        return d.get().getCodigo();
    }

    UUID uuid(String campo, String valor) {
        String t = recortar(valor);
        if (t == null) {
            return null;
        }
        try {
            if (t.length() != 36) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(t);
        } catch (IllegalArgumentException e) {
            errores.add(new ErrorCampo(campo, "identificador inválido"));
            return null;
        }
    }

    /** Devuelve {page, size} ya validados (valores por defecto 0 y 20). */
    int[] paginacion(String page, String size, int maxSize) {
        int p = entero("page", page, 0, Integer.MAX_VALUE, 0, "debe ser un entero mayor o igual a 0");
        int s = entero("size", size, 1, maxSize, 20, "debe estar entre 1 y " + maxSize);
        // BUG-J1: en long, porque (p + 1) con p = Integer.MAX_VALUE desbordaba el int y el límite no se aplicaba
        if (p >= 0 && s >= 1 && ((long) p + 1) * s > LIMITE_RESULTADOS) {
            errores.add(new ErrorCampo("page", "no se puede paginar más allá de 10.000 resultados"));
        }
        return new int[] {p, s};
    }

    private int entero(String campo, String valor, int min, int max, int defecto, String mensaje) {
        String t = recortar(valor);
        if (t == null) {
            return defecto;
        }
        try {
            int n = Integer.parseInt(t);
            if (n < min || n > max) {
                errores.add(new ErrorCampo(campo, mensaje));
                return defecto;
            }
            return n;
        } catch (NumberFormatException e) {
            errores.add(new ErrorCampo(campo, mensaje));
            return defecto;
        }
    }

    void lanzarSiHayErrores() {
        if (!errores.isEmpty()) {
            throw new ParametroInvalidoException(errores);
        }
    }
}
