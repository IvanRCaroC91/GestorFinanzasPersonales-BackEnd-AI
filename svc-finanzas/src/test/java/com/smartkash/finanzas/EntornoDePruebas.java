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
 *
 * <p>Seguridad: el servicio se niega a arrancar si no sabe de dónde descargar las claves públicas
 * ni qué emisor esperar. Aquí se le dan las mismas variables de entorno que en la realidad
 * ({@code FINANZAS_JWKS_URI} y {@code FINANZAS_TOKEN_EMISOR}), con valores inventados en el
 * dominio {@code .invalid}, que por estándar (RFC 2606, nombres reservados para pruebas) no
 * existe en internet: si algo intentara descargar las claves, fallaría en lugar de conectarse a
 * un servidor real. Se dan las variables y no las propiedades finales para que también se
 * pruebe cómo las lee {@code application.yml}, y para que una prueba pueda sustituir la
 * dirección de las claves por la de su propio servidor.</p>
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
        Map<String, Object> valores = Map.of(
                "eureka.client.enabled", "false",
                "FINANZAS_JWKS_URI", "http://identidad.pruebas.invalid/.well-known/jwks.json",
                "FINANZAS_TOKEN_EMISOR", TokensDePrueba.EMISOR);
        contexto.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("entorno-de-pruebas", valores));
    }
}