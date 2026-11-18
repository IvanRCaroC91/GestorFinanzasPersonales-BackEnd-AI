package com.smartkash.identidad.limpieza.adaptadores.persistencia;

import com.smartkash.identidad.PruebaConContenedores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba en un PostgreSQL real qué filas borra la limpieza y cuáles conserva.
 *
 * <p>Qué hace: guarda filas a uno y otro lado de una fecha límite, pide al adaptador que
 * borre y mira directamente en las tablas qué quedó. Las filas justo en el límite se
 * conservan: solo se borra lo que es estrictamente anterior.</p>
 *
 * <p>Por qué fechas del año 2000: la base de datos la comparten todas las pruebas, y las demás
 * guardan filas con fechas actuales. Con un límite en 2001, la limpieza de esta prueba solo
 * puede alcanzar sus propias filas.</p>
 *
 * <p>El adaptador se crea aquí con lotes de 2 filas para que las pruebas recorran varias
 * vueltas del borrado por lotes con pocos datos.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
class PersistenciaDeLaLimpiezaTest extends PruebaConContenedores {

    private static final Instant LIMITE = Instant.parse("2001-01-01T00:00:00Z");
    private static final Instant ANTES = LIMITE.minusSeconds(1);
    private static final Instant DESPUES = LIMITE.plusSeconds(1);
    private static final SecureRandom AZAR = new SecureRandom();

    @Autowired
    private JdbcClient jdbc;

    private AlmacenDeDatosCaducadosPostgres almacen() {
        return new AlmacenDeDatosCaducadosPostgres(jdbc, 2);
    }

    // ---------- Contadores de intentos ----------

    /**
     * Se borran los contadores cuya última actividad (inicio de ventana o fin de bloqueo, la
     * más reciente de las dos) es anterior al límite. Un contador con la ventana antigua pero
     * un bloqueo que termina después del límite se conserva.
     */
    @Test
    void borra_los_contadores_sin_actividad_desde_el_limite() {
        String viejoSinBloqueo = contador(ANTES, null);
        String viejoConBloqueoViejo = contador(ANTES.minusSeconds(60), ANTES);
        String ventanaViejaBloqueoReciente = contador(ANTES, DESPUES);
        String justoEnElLimite = contador(LIMITE, null);
        String reciente = contador(DESPUES, null);

        long borrados = almacen().borrarContadoresSinActividadDesde(LIMITE);

        assertThat(borrados).isEqualTo(2);
        assertThat(existeContador(viejoSinBloqueo)).isFalse();
        assertThat(existeContador(viejoConBloqueoViejo)).isFalse();
        assertThat(existeContador(ventanaViejaBloqueoReciente)).isTrue();
        assertThat(existeContador(justoEnElLimite)).isTrue();
        assertThat(existeContador(reciente)).isTrue();
    }

    /** Con más filas que el tamaño del lote, el borrado sigue hasta no dejar ninguna. */
    @Test
    void borra_por_lotes_hasta_terminar() {
        List<String> claves = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            claves.add(contador(ANTES.minusSeconds(i), null));
        }

        assertThat(almacen().borrarContadoresSinActividadDesde(LIMITE)).isEqualTo(5);
        assertThat(claves).noneMatch(this::existeContador);
    }

    // ---------- Tokens de renovación ----------

    /** Se borran los tokens de renovación que caducaron antes del límite; los demás quedan. */
    @Test
    void borra_los_tokens_de_renovacion_caducados_antes_del_limite() {
        UUID cuenta = cuenta();
        UUID viejo1 = tokenRenovacion(cuenta, ANTES);
        UUID viejo2 = tokenRenovacion(cuenta, ANTES.minusSeconds(10));
        UUID viejo3 = tokenRenovacion(cuenta, ANTES.minusSeconds(20));
        UUID justoEnElLimite = tokenRenovacion(cuenta, LIMITE);
        UUID vigente = tokenRenovacion(cuenta, DESPUES);

        long borrados = almacen().borrarTokensDeRenovacionCaducadosAntesDe(LIMITE);

        assertThat(borrados).isEqualTo(3);
        assertThat(existe("tokens_renovacion", viejo1)).isFalse();
        assertThat(existe("tokens_renovacion", viejo2)).isFalse();
        assertThat(existe("tokens_renovacion", viejo3)).isFalse();
        assertThat(existe("tokens_renovacion", justoEnElLimite)).isTrue();
        assertThat(existe("tokens_renovacion", vigente)).isTrue();
    }

    // ---------- Enlaces de un solo uso ----------

    /**
     * Se borran los enlaces de un solo uso que caducaron antes del límite, se hayan usado o
     * no y sean del tipo que sean; los demás quedan.
     */
    @Test
    void borra_los_enlaces_de_un_solo_uso_caducados_antes_del_limite() {
        UUID cuenta = cuenta();
        UUID sinUsar = enlace(cuenta, "VERIFICACION_CORREO", ANTES, null);
        UUID usado = enlace(cuenta, "VERIFICACION_CORREO", ANTES, ANTES.minusSeconds(3600));
        UUID deRecuperacion = enlace(cuenta, "RECUPERACION", ANTES, null);
        UUID justoEnElLimite = enlace(cuenta, "VERIFICACION_CORREO", LIMITE, null);
        UUID vigente = enlace(cuenta, "VERIFICACION_CORREO", DESPUES, null);

        long borrados = almacen().borrarEnlacesDeUnSoloUsoCaducadosAntesDe(LIMITE);

        assertThat(borrados).isEqualTo(3);
        assertThat(existe("tokens_un_solo_uso", sinUsar)).isFalse();
        assertThat(existe("tokens_un_solo_uso", usado)).isFalse();
        assertThat(existe("tokens_un_solo_uso", deRecuperacion)).isFalse();
        assertThat(existe("tokens_un_solo_uso", justoEnElLimite)).isTrue();
        assertThat(existe("tokens_un_solo_uso", vigente)).isTrue();
    }

    /** La cuenta dueña de los tokens no se toca: solo se borran los tokens. */
    @Test
    void no_borra_la_cuenta_duena_de_los_tokens() {
        UUID cuenta = cuenta();
        tokenRenovacion(cuenta, ANTES);
        enlace(cuenta, "VERIFICACION_CORREO", ANTES, null);

        almacen().borrarTokensDeRenovacionCaducadosAntesDe(LIMITE);
        almacen().borrarEnlacesDeUnSoloUsoCaducadosAntesDe(LIMITE);

        assertThat(existe("usuarios", cuenta)).isTrue();
    }

    // ---------- Ayudas ----------

    private String contador(Instant ventanaInicio, Instant bloqueadoHasta) {
        String clave = huellaAlAzar();
        jdbc.sql("""
                        INSERT INTO bloqueos_acceso (clave_hash, tipo, fallos, ventana_inicio, bloqueado_hasta)
                        VALUES (?, 'IP', 1, ?, ?)
                        """)
                .params(clave, Timestamp.from(ventanaInicio),
                        bloqueadoHasta == null ? null : Timestamp.from(bloqueadoHasta))
                .update();
        return clave;
    }

    private UUID cuenta() {
        UUID id = UUID.randomUUID();
        String correo = "limpieza." + id + "@correo.com";
        jdbc.sql("""
                        INSERT INTO usuarios (id, email, email_normalizado, nombre, hash_contrasena)
                        VALUES (?, ?, ?, 'Ana', 'hash-cualquiera')
                        """)
                .params(id, correo, correo)
                .update();
        return id;
    }

    private UUID tokenRenovacion(UUID cuenta, Instant expiraEn) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO tokens_renovacion (id, familia_id, usuario_id, hash_token, emitido_en, expira_en)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .params(id, UUID.randomUUID(), cuenta, huellaAlAzar(),
                        Timestamp.from(expiraEn.minus(Duration.ofDays(7))), Timestamp.from(expiraEn))
                .update();
        return id;
    }

    private UUID enlace(UUID cuenta, String tipo, Instant expiraEn, Instant usadoEn) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO tokens_un_solo_uso (id, usuario_id, tipo, hash_token, expira_en, usado_en, creado_en)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, cuenta, tipo, huellaAlAzar(), Timestamp.from(expiraEn),
                        usadoEn == null ? null : Timestamp.from(usadoEn),
                        Timestamp.from(expiraEn.minus(Duration.ofHours(24))))
                .update();
        return id;
    }

    private boolean existeContador(String clave) {
        return jdbc.sql("SELECT count(*) FROM bloqueos_acceso WHERE clave_hash = ?")
                .param(clave).query(Integer.class).single() == 1;
    }

    private boolean existe(String tabla, UUID id) {
        return jdbc.sql("SELECT count(*) FROM " + tabla + " WHERE id = ?")
                .param(id).query(Integer.class).single() == 1;
    }

    private static String huellaAlAzar() {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
