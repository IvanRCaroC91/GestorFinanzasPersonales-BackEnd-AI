package com.smartkash.identidad;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Path;
import java.util.Map;

/**
 * Entrega una clave de firma y un emisor de tokens a todas las pruebas que arrancan el
 * servicio completo.
 *
 * <p>Qué es un inicializador: código que Spring ejecuta justo antes de crear los beans. Este
 * se registra en {@code src/test/resources/META-INF/spring.factories}, así que solo existe en
 * las pruebas: el servicio real nunca lo ve.</p>
 *
 * <p>Por qué hace falta: el servicio no arranca sin clave de firma. En lugar de guardar una
 * clave en el repositorio, aquí se genera una RSA de 3072 bits en memoria, se escribe en un
 * archivo temporal y se pasa su ruta como si viniera de la variable de entorno. Se genera una
 * sola vez por ejecución, porque generarla tarda un momento.</p>
 */
public class ClaveDeFirmaParaPruebas
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** Emisor ficticio que llevan los tokens de las pruebas. */
    public static final String EMISOR = "https://identidad.pruebas.invalid";

    private static Path archivo;

    /**
     * Añade la ruta de la clave y el emisor a la configuración, por delante de cualquier otra
     * fuente.
     *
     * @param contexto el contexto que Spring está preparando
     */
    @Override
    public void initialize(ConfigurableApplicationContext contexto) {
        contexto.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "clave-de-firma-para-pruebas",
                Map.of("identidad.token.clave-firma-ruta", archivoDeClave().toString(),
                        "identidad.token.emisor", EMISOR)));
    }

    private static synchronized Path archivoDeClave() {
        if (archivo == null) {
            archivo = ClavesDePrueba.archivoTemporal(
                    ClavesDePrueba.pem(ClavesDePrueba.rsa(3072).getPrivate()));
        }
        return archivo;
    }
}
