package com.smartkash.identidad.cuenta.adaptadores.persistencia;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.EstadoCuenta;
import com.smartkash.identidad.cuenta.dominio.Rol;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el repositorio de usuarios encuentra una cuenta por su correo y la devuelve
 * completa, leyendo de un PostgreSQL real.
 *
 * <p>Qué hace: arranca un PostgreSQL desechable dentro de Docker, deja que Flyway cree las
 * tablas, guarda cuentas con el propio repositorio y las vuelve a leer. Es la consulta que
 * usará el inicio de sesión para comparar la contraseña con el hash guardado.</p>
 *
 * <p>De dónde vienen los datos: cada prueba crea su cuenta con un correo al azar, para que
 * ninguna dependa de lo que dejó otra. A dónde van: a una base de datos que se destruye al
 * terminar.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
@Testcontainers
class BusquedaDeCuentasTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18").withInitScript("db/roles-de-prueba.sql");

    /* Un valor cualquiera con forma de hash; aquí no se calcula ninguno de verdad. */
    private static final String HASH = "$argon2id$v=19$m=1,t=1,p=1$c2FsdGRlcHJ1ZWJh$aGFzaGRlcHJ1ZWJh";

    @Autowired
    private RepositorioUsuarios usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * La cuenta leída es igual a la que se guardó: mismo identificador, nombre, correo como
     * lo escribió la persona, correo normalizado, hash, rol y estado.
     */
    @Test
    void devuelve_la_cuenta_con_todos_sus_datos() {
        String local = "Ana." + UUID.randomUUID();
        Usuario guardada = usuarioCon(local + "@Correo.COM");
        usuarios.guardar(guardada);

        Optional<Usuario> leida = usuarios.buscarPorCorreo(guardada.correo());

        assertThat(leida).contains(guardada);
        assertThat(leida.orElseThrow().correoOriginal()).isEqualTo(local + "@Correo.COM");
    }

    /**
     * Da igual con qué mayúsculas se escriba el correo al buscar: la forma normalizada es la
     * misma, así que se encuentra la misma cuenta.
     */
    @Test
    void encuentra_la_cuenta_aunque_el_correo_se_escriba_con_otras_mayusculas() {
        String correo = correoAlAzar();
        Usuario guardada = usuarioCon(correo);
        usuarios.guardar(guardada);

        Optional<Usuario> leida = usuarios.buscarPorCorreo(new CorreoNormalizado(correo.toUpperCase()));

        assertThat(leida).map(Usuario::id).contains(guardada.id());
    }

    /** Si nadie se registró con ese correo, la respuesta es "no hay cuenta", sin error. */
    @Test
    void no_devuelve_nada_si_el_correo_no_tiene_cuenta() {
        assertThat(usuarios.buscarPorCorreo(new CorreoNormalizado(correoAlAzar()))).isEmpty();
    }

    /**
     * El rol y el estado se leen de la fila, no se suponen. Aquí se cambian directamente en
     * la tabla, como ocurrirá cuando la persona verifique su correo, y la lectura debe
     * reflejar los valores nuevos.
     */
    @Test
    void lee_el_rol_y_el_estado_que_hay_en_la_base() {
        Usuario guardada = usuarioCon(correoAlAzar());
        usuarios.guardar(guardada);
        jdbc.update("UPDATE usuarios SET rol = 'ADMIN', estado = 'ACTIVA' WHERE id = ?", guardada.id());

        Usuario leida = usuarios.buscarPorCorreo(guardada.correo()).orElseThrow();

        assertThat(leida.rol()).isEqualTo(Rol.ADMIN);
        assertThat(leida.estado()).isEqualTo(EstadoCuenta.ACTIVA);
    }

    private static Usuario usuarioCon(String correo) {
        return Usuario.nuevo("Ana", correo, new CorreoNormalizado(correo), HASH);
    }

    private static String correoAlAzar() {
        return "persona." + UUID.randomUUID() + "@correo.com";
    }
}