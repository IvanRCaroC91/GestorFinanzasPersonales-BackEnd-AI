package com.smartkash.identidad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que las migraciones dejan la base de datos lista para el registro de usuarios.
 *
 * <p>Qué hace: arranca un PostgreSQL real y desechable dentro de Docker, levanta el servicio
 * contra él y deja que Flyway aplique los archivos SQL de {@code db/migration}. Después
 * pregunta a la base de datos si quedó como se espera.</p>
 *
 * <p>De dónde vienen los datos: de los propios archivos de migración. A dónde van: a una base
 * de datos que se destruye al terminar la prueba, así que no queda rastro.</p>
 *
 * <p>Necesita Docker abierto. Si Docker no está disponible, la prueba falla al arrancar.</p>
 */
@SpringBootTest
@Testcontainers
class MigracionesTest {

    /*
     * El contenedor es estático para que se arranque una sola vez para toda la clase.
     * @ServiceConnection le dice a Spring "conéctate a este contenedor": no hace falta escribir
     * la dirección, el usuario ni la contraseña en ningún archivo.
     * El script inicial crea el rol de la aplicación, que la primera migración da por existente.
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18").withInitScript("db/roles-de-prueba.sql");

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * La primera migración debe crear las tablas que usa el registro: la de usuarios y la de
     * autorizaciones.
     */
    @Test
    void crea_las_tablas_del_registro() {
        Integer tablas = jdbc.queryForObject(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN ('usuarios', 'autorizaciones')
                """,
                Integer.class);

        assertThat(tablas).isEqualTo(2);
    }

    /**
     * La segunda migración debe publicar la versión inicial de la política de tratamiento de
     * datos. Sin ella nadie podría registrarse, porque el registro exige aceptar una versión
     * que exista.
     */
    @Test
    void publica_la_version_inicial_de_la_politica() {
        Integer politicas = jdbc.queryForObject(
                "SELECT count(*) FROM politicas_tratamiento WHERE version = '1.0'",
                Integer.class);

        assertThat(politicas).isEqualTo(1);
    }

    /**
     * La auditoría es de solo inserción: el rol con el que se conecta la aplicación puede
     * añadir filas, pero no modificarlas ni borrarlas. Así un fallo o un ataque en el servicio
     * no puede reescribir el historial.
     */
    @Test
    void la_aplicacion_no_puede_modificar_ni_borrar_la_auditoria() {
        Boolean puedeInsertar = jdbc.queryForObject(
                "SELECT has_table_privilege('identidad_app', 'auditoria', 'INSERT')", Boolean.class);
        Boolean puedeModificar = jdbc.queryForObject(
                "SELECT has_table_privilege('identidad_app', 'auditoria', 'UPDATE')", Boolean.class);
        Boolean puedeBorrar = jdbc.queryForObject(
                "SELECT has_table_privilege('identidad_app', 'auditoria', 'DELETE')", Boolean.class);

        assertThat(puedeInsertar).isTrue();
        assertThat(puedeModificar).isFalse();
        assertThat(puedeBorrar).isFalse();
    }

    /**
     * El historial de migraciones es la tabla donde Flyway anota qué archivos ya aplicó. El
     * rol con el que se conecta la aplicación no debe poder leerla ni cambiarla: solo la
     * usa quien aplica las migraciones. Si la aplicación pudiera alterarla, un fallo o un
     * ataque en el servicio podría hacer que una migración se repita o se dé por aplicada
     * sin estarlo.
     */
    @Test
    void la_aplicacion_no_tiene_permisos_sobre_el_historial_de_migraciones() {
        for (String permiso : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE")) {
            Boolean loTiene = jdbc.queryForObject(
                    "SELECT has_table_privilege('identidad_app', 'flyway_schema_history', ?)",
                    Boolean.class, permiso);

            assertThat(loTiene).as("permiso %s sobre el historial de migraciones", permiso).isFalse();
        }
    }

    /**
     * Retirar los permisos sobre el historial no debe afectar a las tablas con las que
     * trabaja la aplicación: sigue pudiendo leer y escribir cuentas.
     */
    @Test
    void la_aplicacion_conserva_sus_permisos_sobre_las_tablas_de_datos() {
        Boolean puedeLeer = jdbc.queryForObject(
                "SELECT has_table_privilege('identidad_app', 'usuarios', 'SELECT')", Boolean.class);
        Boolean puedeInsertar = jdbc.queryForObject(
                "SELECT has_table_privilege('identidad_app', 'usuarios', 'INSERT')", Boolean.class);

        assertThat(puedeLeer).isTrue();
        assertThat(puedeInsertar).isTrue();
    }
}
