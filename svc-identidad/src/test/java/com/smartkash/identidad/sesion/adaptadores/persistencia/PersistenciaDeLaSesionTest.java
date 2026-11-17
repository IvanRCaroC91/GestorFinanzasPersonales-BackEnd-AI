package com.smartkash.identidad.sesion.adaptadores.persistencia;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import com.smartkash.identidad.sesion.dominio.ControlDeIntentos;
import com.smartkash.identidad.sesion.dominio.TipoDeContador;
import com.smartkash.identidad.sesion.dominio.TokenRenovacion;
import com.smartkash.identidad.sesion.puertos.RepositorioBloqueos;
import com.smartkash.identidad.sesion.puertos.RepositorioTokensRenovacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que los contadores de bloqueo y los tokens de renovación se guardan y se leen
 * bien en un PostgreSQL real, también cuando llegan varios fallos a la vez.
 *
 * <p>Qué hace: arranca un PostgreSQL desechable dentro de Docker, deja que Flyway cree las
 * tablas y usa los dos repositorios tal como los usará el caso de uso de inicio de sesión.
 * Después mira directamente en las tablas, con SQL, para confirmar qué quedó guardado.</p>
 *
 * <p>Las pruebas de fallos simultáneos lanzan varios hilos (tareas que el ordenador ejecuta
 * a la vez) que anotan un fallo sobre el mismo contador en el mismo instante. Si el
 * repositorio leyera, calculara y guardara sin proteger la fila, dos hilos podrían leer el
 * mismo valor y guardar el mismo resultado: se perdería un fallo.</p>
 *
 * <p>De dónde vienen los datos: cada prueba usa claves y cuentas al azar, para que ninguna
 * dependa de lo que dejó otra. A dónde van: a una base de datos que se destruye al terminar.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
@Testcontainers
class PersistenciaDeLaSesionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18").withInitScript("db/roles-de-prueba.sql");

    private static final Instant AHORA = Instant.parse("2026-11-17T10:00:00Z");

    /* Un valor cualquiera con forma de hash de contraseña; aquí no se calcula ninguno. */
    private static final String HASH = "$argon2id$v=19$m=1,t=1,p=1$c2FsdGRlcHJ1ZWJh$aGFzaGRlcHJ1ZWJh";

    private static final int HILOS = 10;

    private static final SecureRandom AZAR = new SecureRandom();

    @Autowired
    private RepositorioBloqueos bloqueos;

    @Autowired
    private RepositorioTokensRenovacion tokensRenovacion;

    @Autowired
    private RepositorioUsuarios usuarios;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------- Contadores de bloqueo ----------

    /** Una clave sin fallos anotados no tiene contador. */
    @Test
    void sin_fallos_no_hay_contador() {
        assertThat(bloqueos.buscar(claveAlAzar())).isEmpty();
    }

    /**
     * El primer fallo crea la fila con un fallo, la ventana abierta en ese momento, sin
     * bloqueo y con el tipo de contador. Al buscarla se obtiene lo mismo que se devolvió.
     */
    @Test
    void el_primer_fallo_crea_el_contador() {
        String clave = claveAlAzar();

        ControlDeIntentos devuelto = bloqueos.anotarFallo(clave, TipoDeContador.CUENTA, AHORA);

        assertThat(devuelto).isEqualTo(new ControlDeIntentos(1, AHORA, null));
        assertThat(bloqueos.buscar(clave)).contains(devuelto);
        Map<String, Object> fila = fila(clave);
        assertThat(fila.get("tipo")).isEqualTo("CUENTA");
        assertThat(((Number) fila.get("fallos")).intValue()).isEqualTo(1);
        assertThat(fila.get("bloqueado_hasta")).isNull();
    }

    /**
     * Los fallos siguientes aplican las reglas del dominio con el límite de su tipo: el
     * quinto fallo de una cuenta la bloquea 1 minuto, y ese bloqueo queda guardado.
     */
    @Test
    void el_quinto_fallo_de_una_cuenta_guarda_el_bloqueo() {
        String clave = claveAlAzar();

        ControlDeIntentos ultimo = null;
        for (int i = 0; i < 5; i++) {
            ultimo = bloqueos.anotarFallo(clave, TipoDeContador.CUENTA, AHORA);
        }

        ControlDeIntentos esperado = new ControlDeIntentos(5, AHORA, AHORA.plus(Duration.ofMinutes(1)));
        assertThat(ultimo).isEqualTo(esperado);
        assertThat(bloqueos.buscar(clave)).contains(esperado);
        assertThat(fila(clave).get("bloqueado_hasta"))
                .isEqualTo(Timestamp.from(AHORA.plus(Duration.ofMinutes(1))));
    }

    /** Una IP tolera más fallos que una cuenta: cinco fallos no la bloquean. */
    @Test
    void una_ip_usa_su_propio_limite() {
        String clave = claveAlAzar();

        for (int i = 0; i < 5; i++) {
            bloqueos.anotarFallo(clave, TipoDeContador.IP, AHORA);
        }

        assertThat(bloqueos.buscar(clave)).contains(new ControlDeIntentos(5, AHORA, null));
        assertThat(fila(clave).get("tipo")).isEqualTo("IP");
    }

    /** Olvidar un contador borra su fila; olvidar uno que no existe no da error. */
    @Test
    void olvidar_borra_el_contador() {
        String clave = claveAlAzar();
        bloqueos.anotarFallo(clave, TipoDeContador.CUENTA, AHORA);

        bloqueos.olvidar(clave);
        bloqueos.olvidar(claveAlAzar());

        assertThat(bloqueos.buscar(clave)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM bloqueos_acceso WHERE clave_hash = ?", Integer.class, clave))
                .isZero();
    }

    /**
     * Varios fallos a la vez sobre un contador que todavía no existe: todos intentan crear la
     * fila al mismo tiempo. Solo uno puede crearla; los demás deben sumar sobre ella sin
     * perderse ni fallar.
     */
    @Test
    void fallos_simultaneos_sobre_un_contador_nuevo_no_se_pierden() throws Exception {
        String clave = claveAlAzar();

        anotarALaVez(clave, TipoDeContador.IP);

        assertThat(bloqueos.buscar(clave)).contains(new ControlDeIntentos(HILOS, AHORA, null));
    }

    /** Varios fallos a la vez sobre un contador que ya existe: todos suman. */
    @Test
    void fallos_simultaneos_sobre_un_contador_existente_no_se_pierden() throws Exception {
        String clave = claveAlAzar();
        bloqueos.anotarFallo(clave, TipoDeContador.IP, AHORA);

        anotarALaVez(clave, TipoDeContador.IP);

        assertThat(bloqueos.buscar(clave)).contains(new ControlDeIntentos(HILOS + 1, AHORA, null));
    }

    /**
     * Diez fallos a la vez contra una cuenta: el quinto la bloquea y los otros cinco llegan
     * en pleno bloqueo, así que no suman ni alargan la espera. Queda exactamente el bloqueo
     * de 1 minuto con 5 fallos, ni más ni menos.
     */
    @Test
    void fallos_simultaneos_contra_una_cuenta_respetan_el_bloqueo() throws Exception {
        String clave = claveAlAzar();

        anotarALaVez(clave, TipoDeContador.CUENTA);

        assertThat(bloqueos.buscar(clave))
                .contains(new ControlDeIntentos(5, AHORA, AHORA.plus(Duration.ofMinutes(1))));
    }

    // ---------- Tokens de renovación ----------

    /**
     * El token se guarda con su identificador, familia, dueño, huella y fechas. Las columnas
     * de uso y revocación quedan vacías: el token está recién emitido.
     */
    @Test
    void guarda_el_token_de_renovacion() {
        Usuario cuenta = cuentaGuardada();
        String huella = claveAlAzar();
        TokenRenovacion token = TokenRenovacion.deSesionNueva(cuenta.id(), huella, AHORA);

        tokensRenovacion.guardar(token);

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT * FROM tokens_renovacion WHERE id = ?", token.id());
        assertThat(fila.get("familia_id")).isEqualTo(token.familiaId());
        assertThat(fila.get("usuario_id")).isEqualTo(cuenta.id());
        assertThat(fila.get("hash_token")).isEqualTo(huella);
        assertThat(fila.get("emitido_en")).isEqualTo(Timestamp.from(AHORA));
        assertThat(fila.get("expira_en")).isEqualTo(Timestamp.from(AHORA.plus(Duration.ofDays(7))));
        assertThat(fila.get("usado_en")).isNull();
        assertThat(fila.get("revocado_en")).isNull();
        assertThat(fila.get("motivo_revocacion")).isNull();
    }

    /** Dos inicios de sesión de la misma cuenta quedan como dos filas de familias distintas. */
    @Test
    void cada_sesion_queda_en_su_propia_familia() {
        Usuario cuenta = cuentaGuardada();

        tokensRenovacion.guardar(TokenRenovacion.deSesionNueva(cuenta.id(), claveAlAzar(), AHORA));
        tokensRenovacion.guardar(TokenRenovacion.deSesionNueva(cuenta.id(), claveAlAzar(), AHORA));

        assertThat(jdbc.queryForObject(
                "SELECT count(DISTINCT familia_id) FROM tokens_renovacion WHERE usuario_id = ?",
                Integer.class, cuenta.id()))
                .isEqualTo(2);
    }

    // ---------- Ayudas ----------

    /*
     * Lanza HILOS tareas que esperan en una "puerta de salida" (CountDownLatch) y, cuando se
     * abre, anotan un fallo todas a la vez. Después espera a que terminen y, si alguna lanzó
     * un error, lo deja salir para que la prueba falle con él.
     */
    private void anotarALaVez(String clave, TipoDeContador tipo) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(HILOS);
        try {
            List<Future<ControlDeIntentos>> resultados = new ArrayList<>();
            for (int i = 0; i < HILOS; i++) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    return bloqueos.anotarFallo(clave, tipo, AHORA);
                }));
            }
            salida.countDown();
            for (Future<ControlDeIntentos> resultado : resultados) {
                resultado.get(30, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }
    }

    private Map<String, Object> fila(String clave) {
        return jdbc.queryForMap("SELECT * FROM bloqueos_acceso WHERE clave_hash = ?", clave);
    }

    private Usuario cuentaGuardada() {
        String correo = "persona." + UUID.randomUUID() + "@correo.com";
        Usuario cuenta = Usuario.nuevo("Ana", correo, new CorreoNormalizado(correo), HASH);
        usuarios.guardar(cuenta);
        return cuenta;
    }

    /** 64 cifras hexadecimales al azar: la forma de una huella SHA-256. */
    private static String claveAlAzar() {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}