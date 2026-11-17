package com.smartkash.identidad;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Path;
import java.util.Map;

/**
 * Entrega a todas las pruebas que arrancan el servicio completo la configuración que en la
 * realidad llega por variables de entorno: clave de firma, emisor de tokens, correo y dirección
 * de la web para los enlaces.
 *
 * <p>Qué es un inicializador: código que Spring ejecuta justo antes de crear los beans. Este
 * se registra en {@code src/test/resources/META-INF/spring.factories}, así que solo existe en
 * las pruebas: el servicio real nunca lo ve.</p>
 *
 * <p>Por qué hace falta: el servicio no arranca sin esa configuración. En lugar de guardar una
 * clave en el repositorio, aquí se genera una RSA de 3072 bits en memoria, se escribe en un
 * archivo temporal y se pasa su ruta como si viniera de la variable de entorno. Se genera una
 * sola vez por ejecución, porque generarla tarda un momento.</p>
 *
 * <p>Correo: apunta a un puerto de este equipo donde no escucha nadie. Ninguna prueba debe
 * enviar correos reales; la que prueba el envío usa su propio servidor de correo de pruebas.</p>
 */
public class EntornoDePruebas
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** Emisor ficticio que llevan los tokens de las pruebas. */
    public static final String EMISOR = "https://identidad.pruebas.invalid";

    /** Remitente ficticio de los correos de las pruebas. */
    public static final String REMITENTE = "no-responder@pruebas.invalid";

    private static Path archivo;

    /**
     * Añade la configuración de pruebas por delante de cualquier otra fuente.
     *
     * @param contexto el contexto que Spring está preparando
     */
    @Override
    public void initialize(ConfigurableApplicationContext contexto) {
        contexto.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "entorno-de-pruebas",
                Map.of("identidad.token.clave-firma-ruta", archivoDeClave().toString(),
                        "identidad.token.emisor", EMISOR,
                        "spring.mail.host", "127.0.0.1",
                        "spring.mail.port", "9",
                        "identidad.correo.remitente", REMITENTE,
                        "identidad.verificacion.direccion-base", "https://app.pruebas.invalid")));
    }

    private static synchronized Path archivoDeClave() {
        if (archivo == null) {
            archivo = ClavesDePrueba.archivoTemporal(
                    ClavesDePrueba.pem(ClavesDePrueba.rsa(3072).getPrivate()));
        }
        return archivo;
    }
}
