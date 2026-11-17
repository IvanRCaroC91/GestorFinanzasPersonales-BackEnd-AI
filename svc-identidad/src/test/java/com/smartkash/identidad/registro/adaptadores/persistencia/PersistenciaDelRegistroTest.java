package com.smartkash.identidad.registro.adaptadores.persistencia;

import com.smartkash.identidad.registro.dominio.Autorizacion;
import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.CorreoYaRegistrado;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.registro.puertos.RepositorioAutorizaciones;
import com.smartkash.identidad.registro.puertos.RepositorioPoliticas;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que los adaptadores de persistencia del registro guardan y leen bien en un
 * PostgreSQL real.
 *
 * <p>Qué hace: arranca un PostgreSQL desechable dentro de Docker, deja que Flyway cree las
 * tablas y usa los tres repositorios tal como los usará el caso de uso del registro. Después
 * mira directamente en las tablas, con SQL, para confirmar qué quedó guardado.</p>
 *
 * <p>De dónde vienen los datos: cada prueba crea sus propias cuentas con un correo al azar,
 * para que ninguna dependa de lo que dejó otra. A dónde van: a una base de datos que se
 * destruye al terminar.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
@Testcontainers
class PersistenciaDelRegistroTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18").withInitScript("db/roles-de-prueba.sql");

    /* Un valor cualquiera con forma de hash; aquí no se calcula ninguno de verdad. */
    private static final String HASH = "$argon2id$v=19$m=1,t=1,p=1$c2FsdGRlcHJ1ZWJh$aGFzaGRlcHJ1ZWJh";

    @Autowired
    private RepositorioUsuarios usuarios;

    @Autowired
    private RepositorioAutorizaciones autorizaciones;

    @Autowired
    private RepositorioPoliticas politicas;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager gestorDeTransacciones;

    /*
     * Permite ejecutar un bloque de código dentro de una sola transacción: o se guarda todo
     * lo que hace el bloque, o no se guarda nada.
     */
    private TransactionTemplate enUnaTransaccion;

    @BeforeEach
    void preparar() {
        enUnaTransaccion = new TransactionTemplate(gestorDeTransacciones);
    }

    /**
     * Una cuenta guardada conserva el correo como lo escribió la persona, su forma en
     * minúsculas, el hash, y el rol y el estado que decidió el servidor.
     */
    @Test
    void guarda_la_cuenta_con_todos_sus_datos() {
        String local = "Ana." + UUID.randomUUID();
        Usuario ana = usuarioCon(local + "@Correo.COM");

        usuarios.guardar(ana);

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM usuarios WHERE id = ?", ana.id());
        assertThat(fila.get("email")).isEqualTo(local + "@Correo.COM");
        assertThat(fila.get("email_normalizado")).isEqualTo(local.toLowerCase() + "@correo.com");
        assertThat(fila.get("nombre")).isEqualTo("Ana");
        assertThat(fila.get("hash_contrasena")).isEqualTo(HASH);
        assertThat(fila.get("rol")).isEqualTo("USUARIO");
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE_VERIFICACION");
        assertThat(fila.get("creado_en")).isNotNull();
    }

    /** Después de guardar una cuenta, su correo figura como existente. */
    @Test
    void encuentra_un_correo_ya_registrado() {
        Usuario ana = usuarioCon(correoAlAzar());

        usuarios.guardar(ana);

        assertThat(usuarios.existeConCorreo(ana.correo())).isTrue();
    }

    /** Un correo que nadie ha registrado figura como libre. */
    @Test
    void no_encuentra_un_correo_que_nadie_registro() {
        assertThat(usuarios.existeConCorreo(new CorreoNormalizado(correoAlAzar()))).isFalse();
    }

    /**
     * Si llega una segunda cuenta con el mismo correo, aunque esté escrito con otras
     * mayúsculas, el repositorio avisa con palabras del negocio y no crea otra fila.
     */
    @Test
    void rechaza_un_correo_repetido_sin_crear_otra_cuenta() {
        String correo = correoAlAzar();
        usuarios.guardar(usuarioCon(correo));
        Usuario repetido = usuarioCon(correo.toUpperCase());

        assertThatThrownBy(() -> usuarios.guardar(repetido)).isInstanceOf(CorreoYaRegistrado.class);

        assertThat(cuentasConCorreo(correo)).isEqualTo(1);
    }

    /**
     * El aviso de correo repetido no debe estropear la transacción en curso. El caso de uso
     * captura ese aviso y termina con normalidad; si la transacción quedara inservible, la
     * respuesta sería un error y delataría que el correo ya existía.
     */
    @Test
    void el_correo_repetido_no_estropea_la_transaccion() {
        String correo = correoAlAzar();
        usuarios.guardar(usuarioCon(correo));
        Usuario otraPersona = usuarioCon(correoAlAzar());

        enUnaTransaccion.executeWithoutResult(estado -> {
            try {
                usuarios.guardar(usuarioCon(correo));
            } catch (CorreoYaRegistrado esperado) {
                // Igual que hace el caso de uso: se captura y se sigue.
            }
            usuarios.guardar(otraPersona);
        });

        assertThat(usuarios.existeConCorreo(otraPersona.correo())).isTrue();
        assertThat(cuentasConCorreo(correo)).isEqualTo(1);
    }

    /**
     * La prueba de autorización guarda quién aceptó, qué versión, por qué canal y cuándo.
     */
    @Test
    void guarda_la_prueba_de_autorizacion() {
        Usuario ana = usuarioCon(correoAlAzar());
        Instant momento = Instant.parse("2026-11-16T15:30:00Z");
        usuarios.guardar(ana);

        autorizaciones.guardar(Autorizacion.aceptacionDePolitica(ana.id(), "1.0", momento));

        Map<String, Object> fila =
                jdbc.queryForMap("SELECT * FROM autorizaciones WHERE usuario_id = ?", ana.id());
        assertThat(fila.get("tipo")).isEqualTo("POLITICA_TRATAMIENTO");
        assertThat(fila.get("politica_version")).isEqualTo("1.0");
        assertThat(fila.get("otorgada")).isEqualTo(true);
        assertThat(fila.get("canal")).isEqualTo("WEB");
        assertThat(((Timestamp) fila.get("registrada_en")).toInstant()).isEqualTo(momento);
    }

    /**
     * No puede quedar una prueba de autorización de una cuenta que no existe: la base de
     * datos la rechaza.
     */
    @Test
    void rechaza_una_autorizacion_de_una_cuenta_inexistente() {
        Autorizacion huerfana =
                Autorizacion.aceptacionDePolitica(UUID.randomUUID(), "1.0", Instant.now());

        assertThatThrownBy(() -> autorizaciones.guardar(huerfana))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * La cuenta y su prueba de autorización se guardan juntas o no se guarda ninguna: si
     * algo falla después de guardarlas dentro de la misma transacción, desaparecen las dos.
     */
    @Test
    void si_la_transaccion_falla_no_queda_ni_la_cuenta_ni_la_autorizacion() {
        Usuario ana = usuarioCon(correoAlAzar());

        assertThatThrownBy(() -> enUnaTransaccion.executeWithoutResult(estado -> {
            usuarios.guardar(ana);
            autorizaciones.guardar(Autorizacion.aceptacionDePolitica(ana.id(), "1.0", Instant.now()));
            throw new IllegalStateException("Fallo simulado después de guardar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(usuarios.existeConCorreo(ana.correo())).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM autorizaciones WHERE usuario_id = ?", Integer.class, ana.id()))
                .isZero();
    }

    /** Con solo la versión inicial publicada, esa es la vigente. */
    @Test
    void la_version_vigente_es_la_inicial() {
        assertThat(politicas.versionVigente()).contains("1.0");
    }

    /**
     * Cuando se publica una versión más reciente, esa pasa a ser la vigente. La fila de
     * prueba se borra al final para no afectar a las demás pruebas.
     */
    @Test
    void la_version_vigente_es_la_ultima_publicada() {
        jdbc.update("""
                INSERT INTO politicas_tratamiento (version, publicada_en, url, hash_texto)
                VALUES ('9.9', TIMESTAMPTZ '2099-01-01 00:00:00+00', '/prueba', repeat('0', 64))
                """);
        try {
            assertThat(politicas.versionVigente()).contains("9.9");
        } finally {
            jdbc.update("DELETE FROM politicas_tratamiento WHERE version = '9.9'");
        }
    }

    private static Usuario usuarioCon(String correo) {
        return Usuario.nuevo("Ana", correo, new CorreoNormalizado(correo), HASH);
    }

    private static String correoAlAzar() {
        return "persona." + UUID.randomUUID() + "@correo.com";
    }

    private Integer cuentasConCorreo(String correo) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM usuarios WHERE email_normalizado = ?",
                Integer.class, new CorreoNormalizado(correo).valor());
    }
}