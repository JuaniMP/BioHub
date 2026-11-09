package co.edu.unbosque.biohub.sondaseguridad;

import java.lang.reflect.Constructor;

import org.mockito.Mockito;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.util.ClassUtils;

/**
 * Para pruebas de capa web que cargan TODOS los controladores del servicio: registra un mock de Mockito por cada
 * tipo de la aplicación ({@code co.edu.unbosque.biohub.*}) que reciba por constructor un bean ya registrado y que no
 * tenga bean propio. Así, un controlador nuevo con sus servicios no obliga a editar la prueba (y por tanto no puede
 * quedar fuera de ella). Se usa con {@code @Import}.
 */
public class MocksDeDependencias implements BeanDefinitionRegistryPostProcessor {

    private static final String PAQUETE_APP = "co.edu.unbosque.biohub.";

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        DefaultListableBeanFactory fabrica = (DefaultListableBeanFactory) registry;
        for (String nombre : registry.getBeanDefinitionNames()) {
            BeanDefinition definicion = registry.getBeanDefinition(nombre);
            String clase = definicion.getBeanClassName();
            if (clase == null || !clase.startsWith(PAQUETE_APP)) {
                continue;
            }
            Class<?> tipoBean = ClassUtils.resolveClassName(clase, ClassUtils.getDefaultClassLoader());
            for (Constructor<?> constructor : tipoBean.getDeclaredConstructors()) {
                for (Class<?> tipo : constructor.getParameterTypes()) {
                    if (tipo.getName().startsWith(PAQUETE_APP) && fabrica.getBeanNamesForType(tipo, true, false).length == 0) {
                        registrarMock(registry, tipo);
                    }
                }
            }
        }
    }

    private static <T> void registrarMock(BeanDefinitionRegistry registry, Class<T> tipo) {
        registry.registerBeanDefinition("mock#" + tipo.getName(),
                BeanDefinitionBuilder.genericBeanDefinition(tipo, () -> Mockito.mock(tipo)).getBeanDefinition());
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // sin cambios
    }
}
