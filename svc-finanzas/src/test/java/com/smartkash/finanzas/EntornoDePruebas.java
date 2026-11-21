package com.smartkash.finanzas;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Entrega a las pruebas que arrancan el servicio completo la configuración que en la realidad
 * llega por variables de entorno.
 *
 * <p>Qué es un inicializador: código que Spring ejecuta justo antes de crear los beans. Este se
 * registra en {@code src/test/resources/META-INF/spring.factories}, así que solo existe en las
 * pruebas: el servicio real nunca lo ve.</p>
 *
 * <p>Descubrimiento: el cliente de Eureka queda desactivado, para que las pruebas no intenten
 * registrarse en ningún servidor (lo comprueba {@code DescubrimientoEnPruebasTest}).</p>
 */
public class EntornoDePruebas
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /**
     * Añade la configuración de pruebas por delante de cualquier otra fuente.
     *
     * @param contexto el contexto que Spring está preparando
     */
    @Override
    public void initialize(ConfigurableApplicationContext contexto) {
        Map<String, Object> valores = Map.of("eureka.client.enabled", "false");
        contexto.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("entorno-de-pruebas", valores));
    }
}