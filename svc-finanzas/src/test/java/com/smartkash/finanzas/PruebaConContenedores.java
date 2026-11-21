package com.smartkash.finanzas;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de las pruebas que necesitan PostgreSQL: un solo contenedor para toda la ejecución.
 *
 * <p>Qué hace: declara el contenedor como campo {@code static} y lo arranca una sola vez, la
 * primera vez que Java carga esta clase. Todas las clases de prueba que heredan de ella usan el
 * mismo contenedor, y Testcontainers lo borra al terminar. Arrancar un contenedor tarda varios
 * segundos; compartirlo ahorra ese tiempo en cada clase.</p>
 *
 * <p>Por qué es seguro compartir: las pruebas se ejecutan una detrás de otra, y cada una usa
 * datos propios (identificadores de usuario al azar), así que no se afectan entre sí.</p>
 *
 * <p>Cómo se conecta el servicio: {@code @ServiceConnection} le dice a Spring Boot que tome la
 * dirección, el usuario y la contraseña de la base de datos del contenedor.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
public abstract class PruebaConContenedores {

    /** PostgreSQL desechable, con el rol que conceden las migraciones ya creado. */
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18").withInitScript("db/roles-de-prueba.sql");

    static {
        POSTGRES.start();
    }
}