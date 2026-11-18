package com.smartkash.identidad;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de las pruebas que necesitan PostgreSQL o el servidor de correo de pruebas: un solo
 * contenedor de cada uno para toda la ejecución.
 *
 * <p>Qué hace: declara los dos contenedores como campos {@code static} y los arranca una sola
 * vez, la primera vez que Java carga esta clase. Todas las clases de prueba que heredan de ella
 * usan los mismos contenedores. Testcontainers los borra al terminar la ejecución, igual que
 * antes.</p>
 *
 * <p>Por qué: arrancar un contenedor tarda varios segundos, y antes cada clase arrancaba los
 * suyos. Además, como todas las clases que heredan de aquí tienen la misma configuración,
 * Spring reutiliza el mismo servicio arrancado entre ellas, salvo las que sustituyen beans por
 * dobles de Mockito, que necesitan uno propio.</p>
 *
 * <p>Por qué es seguro compartir: las pruebas se ejecutan una detrás de otra, nunca a la vez. Lo
 * que cada prueba escribe en la base de datos no puede afectar a otra porque usa datos propios
 * (correos, claves e IPs al azar) o deshace lo que publica para todos (las políticas de
 * tratamiento). Los correos se distinguen por su destinatario, que también es único.</p>
 *
 * <p>Cómo se conecta el servicio: {@code @ServiceConnection} le dice a Spring Boot que tome la
 * dirección, el usuario y la contraseña de la base de datos del contenedor. El servidor de correo
 * se indica con {@link #servidorDeCorreo}.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
public abstract class PruebaConContenedores {

    /** Puerto SMTP de Mailpit dentro del contenedor. */
    public static final int PUERTO_SMTP = 1025;

    /** Puerto de la página web y la API de Mailpit dentro del contenedor. */
    public static final int PUERTO_WEB = 8025;

    /** PostgreSQL desechable, con el rol que conceden las migraciones ya creado. */
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18").withInitScript("db/roles-de-prueba.sql");

    /** Servidor de correo de pruebas: acepta todos los correos y los muestra por su API. */
    public static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.33.1"))
                    .withExposedPorts(PUERTO_SMTP, PUERTO_WEB)
                    .waitingFor(Wait.forHttp("/api/v1/messages").forPort(PUERTO_WEB));

    static {
        // Se arrancan a la vez, una sola vez por ejecución.
        Startables.deepStart(POSTGRES, MAILPIT).join();
    }

    /**
     * Apunta el envío de correos del servicio al Mailpit compartido.
     *
     * @param registro donde se añaden las propiedades
     */
    @DynamicPropertySource
    static void servidorDeCorreo(DynamicPropertyRegistry registro) {
        registro.add("spring.mail.host", MAILPIT::getHost);
        registro.add("spring.mail.port", () -> MAILPIT.getMappedPort(PUERTO_SMTP));
    }

    /**
     * Devuelve la dirección base de la API de Mailpit, con la barra final.
     *
     * @return por ejemplo {@code http://localhost:32768/api/v1/}
     */
    public static String apiDeMailpit() {
        return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(PUERTO_WEB) + "/api/v1/";
    }
}