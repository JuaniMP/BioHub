package co.edu.unbosque.biohub.sondaseguridad;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.util.pattern.PathPattern;

/**
 * Prueba de comportamiento de SEC-J-22: recorre TODOS los mappings registrados en el contexto (los que Spring MVC
 * realmente expone, sea cual sea la anotación o la forma de declararlos) y comprueba que un JWT <b>sin roles</b> no
 * llega al cuerpo de ningún endpoint salvo los públicos explícitos. Complementa a {@code ArquitecturaSeguridadTest},
 * que solo mira la forma del código: aquí se prueba qué responde el servicio, así que aparecería cualquier vía que
 * las reglas aún no conozcan.
 * <ul>
 *   <li>{@link #invocacionDirecta}: llama al método del bean registrado con un JWT sin roles en el
 *       {@code SecurityContext} y exige {@link AccessDeniedException}. No depende de la resolución de argumentos de
 *       Spring MVC, que puede responder 400 antes de que {@code @PreAuthorize} se evalúe. Detecta métodos
 *       {@code final} o {@code static} (el proxy no los intercepta) y beans sin proxy de seguridad.</li>
 *   <li>{@link #peticionHttp}: hace la petición por MockMvc con el filtro de seguridad real y exige 403. Se aceptan
 *       400, 406 y 415 como "no concluyente" (el cuerpo o los parámetros de relleno no sirvieron para llegar al
 *       método), porque ese caso lo cubre {@link #invocacionDirecta}.</li>
 * </ul>
 * Vive en {@code sondaseguridad}, fuera del paquete escaneado por la aplicación.
 */
public final class VerificadorDeEndpoints {

    private static final String PAQUETE_APP = "co.edu.unbosque.biohub.";
    private static final Pattern VARIABLE_DE_RUTA = Pattern.compile("\\{([^}:/]+)(?::[^}]*)?}");
    private static final Set<Integer> NO_CONCLUYENTES = Set.of(400, 406, 415);

    private VerificadorDeEndpoints() {
    }

    /** Un mapping registrado en Spring MVC y el método al que apunta. */
    public record Registrado(RequestMappingInfo info, HandlerMethod metodo) {

        public String id() {
            return metodo.getBeanType().getSimpleName() + "." + metodo.getMethod().getName();
        }

        public boolean esDeLaAplicacion() {
            return metodo.getBeanType().getName().startsWith(PAQUETE_APP);
        }

        /** Métodos HTTP del mapping; si no restringe ninguno, los cinco habituales. */
        public List<String> metodosHttp() {
            var metodos = info.getMethodsCondition().getMethods();
            return metodos.isEmpty() ? List.of("GET", "POST", "PUT", "PATCH", "DELETE")
                    : metodos.stream().map(Enum::name).toList();
        }

        public List<String> patrones() {
            var condicion = info.getPathPatternsCondition();
            return condicion != null ? condicion.getPatterns().stream().map(PathPattern::getPatternString).toList()
                    : List.copyOf(info.getPatternValues());
        }

        /** Cada combinación {@code "GET /ruta/{id}"} del mapping. */
        public List<String> rutas() {
            List<String> rutas = new ArrayList<>();
            for (String m : metodosHttp()) {
                for (String p : patrones()) {
                    rutas.add(m + " " + p);
                }
            }
            return rutas;
        }

        /** Público solo si TODAS sus combinaciones método+ruta están en la lista explícita. */
        public boolean esPublico(Set<String> rutasPublicas) {
            return rutasPublicas.containsAll(rutas());
        }

        @Override
        public String toString() {
            return id() + " " + rutas();
        }
    }

    /** Todos los mappings de todos los {@code RequestMappingHandlerMapping} del contexto. */
    public static List<Registrado> registrados(ApplicationContext ctx) {
        List<Registrado> todos = new ArrayList<>();
        for (RequestMappingHandlerMapping mapping : ctx.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            mapping.getHandlerMethods().forEach((info, metodo) -> todos.add(new Registrado(info, metodo)));
        }
        return todos;
    }

    /**
     * Otras vías de exponer rutas que no son {@code @RequestMapping}: rutas funcionales ({@code RouterFunction}) y
     * handlers por URL que no sean el de recursos estáticos ({@code BeanNameUrlHandlerMapping}, etc.).
     */
    public static List<String> otrasVias(ApplicationContext ctx) {
        List<String> otras = new ArrayList<>();
        ctx.getBeansOfType(RouterFunction.class).keySet()
                .forEach(n -> otras.add("RouterFunction '" + n + "' (rutas funcionales, sin @PreAuthorize)"));
        for (AbstractUrlHandlerMapping mapping : ctx.getBeansOfType(AbstractUrlHandlerMapping.class).values()) {
            for (Map.Entry<String, Object> e : mapping.getHandlerMap().entrySet()) {
                if (!(e.getValue() instanceof ResourceHttpRequestHandler)) {
                    otras.add("handler por URL " + e.getKey() + " -> " + e.getValue().getClass().getName());
                }
            }
        }
        return otras;
    }

    private static SecurityContext contextoSinRoles() {
        Jwt jwt = new Jwt("token-de-prueba", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("sub", "sinrol1", "preferred_username", "sinrol1"));
        SecurityContext contexto = new SecurityContextImpl();
        contexto.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        return contexto;
    }

    /**
     * Invoca el método del endpoint con un JWT sin roles. Devuelve la violación, o vacío si se lanzó
     * {@link AccessDeniedException} como debe.
     */
    public static Optional<String> invocacionDirecta(ApplicationContext ctx, Registrado endpoint) {
        Method metodo = endpoint.metodo().getMethod();
        boolean estatico = Modifier.isStatic(metodo.getModifiers());
        Object bean = endpoint.metodo().getBean();
        if (bean instanceof String nombre) {
            bean = ctx.getBean(nombre);
        }
        if (!estatico && !AopUtils.isAopProxy(bean)) {
            return Optional.of(endpoint + ": el bean no tiene proxy de seguridad (@PreAuthorize no se aplica; "
                    + "¿@EnableMethodSecurity desactivado o prePostEnabled=false?)");
        }
        SecurityContext anterior = SecurityContextHolder.getContext();
        try {
            SecurityContextHolder.setContext(contextoSinRoles());
            ReflectionUtils.makeAccessible(metodo);
            metodo.invoke(estatico ? null : bean, valoresPorDefecto(metodo));
            return Optional.of(endpoint + ": con un JWT sin roles el método se ejecutó (no pasó por @PreAuthorize"
                    + (estatico ? "; es static" : (Modifier.isFinal(metodo.getModifiers()) ? "; es final" : "")) + ")");
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof AccessDeniedException) {
                return Optional.empty();
            }
            return Optional.of(endpoint + ": con un JWT sin roles lanzó " + e.getCause().getClass().getName()
                    + " en vez de AccessDeniedException (no pasó por @PreAuthorize"
                    + (Modifier.isFinal(metodo.getModifiers()) ? "; es final" : "") + ")");
        } catch (ReflectiveOperationException e) {
            return Optional.of(endpoint + ": no se pudo invocar (" + e + ")");
        } finally {
            SecurityContextHolder.setContext(anterior);
            SecurityContextHolder.clearContext();
        }
    }

    private static Object[] valoresPorDefecto(Method metodo) {
        Class<?>[] tipos = metodo.getParameterTypes();
        Object[] valores = new Object[tipos.length];
        for (int i = 0; i < tipos.length; i++) {
            valores[i] = tipos[i].isPrimitive() ? Array.get(Array.newInstance(tipos[i], 1), 0) : null;
        }
        return valores;
    }

    /**
     * Petición por MockMvc con un JWT sin roles a cada combinación método+ruta del mapping. Devuelve las violaciones
     * (cualquier respuesta que no sea 403, salvo 400/406/415 que se consideran no concluyentes).
     */
    public static List<String> peticionHttp(MockMvc mvc, Registrado endpoint) throws Exception {
        List<String> violaciones = new ArrayList<>();
        for (String patron : endpoint.patrones()) {
            for (String metodoHttp : endpoint.metodosHttp()) {
                int estado = estadoSinRoles(mvc, endpoint, metodoHttp, patron);
                if (estado != 403 && !NO_CONCLUYENTES.contains(estado)) {
                    violaciones.add(metodoHttp + " " + patron + " (" + endpoint.id() + "): respondió " + estado
                            + " a un JWT sin roles; debía ser 403");
                }
            }
        }
        return violaciones;
    }

    /** Estado HTTP de la ruta con un JWT sin roles (para rutas públicas, que no deben dar 401 ni 403). */
    public static int estadoSinRoles(MockMvc mvc, Registrado endpoint, String metodoHttp, String patron)
            throws Exception {
        MockHttpServletRequestBuilder peticion =
                MockMvcRequestBuilders.request(HttpMethod.valueOf(metodoHttp), rutaConcreta(endpoint, patron));
        peticion.with(jwt()
                .jwt(j -> j.claim("preferred_username", "sinrol1")).authorities(List.of()));
        var produce = endpoint.info().getProducesCondition().getProducibleMediaTypes();
        if (!produce.isEmpty()) {
            peticion.accept(produce.iterator().next());
        }
        if (!metodoHttp.equals("GET") && !metodoHttp.equals("DELETE")) {
            var consume = endpoint.info().getConsumesCondition().getConsumableMediaTypes();
            peticion.contentType(consume.isEmpty() ? MediaType.APPLICATION_JSON : consume.iterator().next());
            peticion.content("{}");
        }
        return mvc.perform(peticion).andReturn().getResponse().getStatus();
    }

    /** Sustituye las variables de ruta por un valor del tipo del parámetro y los comodines por {@code x}. */
    public static String rutaConcreta(Registrado endpoint, String patron) {
        Matcher m = VARIABLE_DE_RUTA.matcher(patron);
        StringBuilder ruta = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(ruta, Matcher.quoteReplacement(valorDeVariable(endpoint, m.group(1))));
        }
        m.appendTail(ruta);
        return ruta.toString().replace("**", "x").replace("*", "x");
    }

    private static String valorDeVariable(Registrado endpoint, String nombre) {
        for (MethodParameter p : endpoint.metodo().getMethodParameters()) {
            PathVariable pv = p.getParameterAnnotation(PathVariable.class);
            if (pv == null) {
                continue;
            }
            p.initParameterNameDiscovery(new org.springframework.core.DefaultParameterNameDiscoverer());
            String nombreParametro = !pv.name().isEmpty() ? pv.name() : !pv.value().isEmpty() ? pv.value()
                    : p.getParameterName();
            if (nombre.equals(nombreParametro)) {
                Class<?> tipo = p.getParameterType();
                if (tipo == UUID.class) {
                    return "00000000-0000-0000-0000-000000000001";
                }
                if (Number.class.isAssignableFrom(tipo) || tipo.isPrimitive()) {
                    return "1";
                }
            }
        }
        return "x";
    }
}
