package com.smartkash.finanzas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que las migraciones dejan la base de datos de finanzas como se espera y que es la
 * propia base de datos, no solo el código Java, la que hace cumplir las reglas de las cuentas.
 *
 * <p>Qué hace: usa el PostgreSQL real y desechable que comparten las pruebas, levanta el servicio
 * contra él y deja que Flyway aplique los archivos SQL de {@code db/migration}. Después inserta
 * filas directamente con SQL, sin pasar por el código del servicio, y mira si la base las acepta
 * o las rechaza.</p>
 *
 * <p>Por qué probar la base y no solo el código: el código podrá validar estas reglas antes de
 * guardar, pero dos peticiones que lleguen a la vez pueden pasar las dos la validación. La
 * restricción de la base es la última barrera y la única que no tiene esa carrera.</p>
 *
 * <p>Cómo se mantienen independientes las pruebas: cada una crea sus propios usuarios con
 * identificadores al azar ({@link UUID#randomUUID()}). Así lo que deja una prueba en la base
 * compartida nunca choca con lo que hace otra, se ejecuten en el orden que sea.</p>
 *
 * <p>Necesita Docker abierto. Si Docker no está disponible, la prueba falla al arrancar.</p>
 */
@SpringBootTest
class MigracionesTest extends PruebaConContenedores {

    /** Rol con el que se conecta la aplicación; lo crea el script de roles de prueba. */
    private static final String ROL_APLICACION = "finanzas_app";

    /** Todos los permisos que PostgreSQL 18 puede conceder sobre una tabla. */
    private static final List<String> PERMISOS_DE_TABLA = List.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER", "MAINTAIN");

    /*
     * JdbcTemplate es la herramienta de Spring para lanzar SQL a mano. Se conecta con el usuario
     * administrador del contenedor, que puede escribir en cualquier tabla: lo que se prueba aquí
     * son las restricciones de la tabla, no los permisos del rol de la aplicación.
     */
    @Autowired
    private JdbcTemplate jdbc;

    /** La primera migración debe crear la tabla de cuentas en el esquema público. */
    @Test
    void crea_la_tabla_de_cuentas() {
        Integer tablas = jdbc.queryForObject(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'cuentas'
                """,
                Integer.class);

        assertThat(tablas).isEqualTo(1);
    }

    /**
     * Una cuenta solo puede ser de uno de los tipos conocidos (efectivo, ahorros, corriente o
     * billetera). Se usa un tipo corto a propósito, para que el rechazo venga de la lista de tipos
     * y no del largo máximo de la columna.
     */
    @Test
    void rechaza_un_tipo_de_cuenta_desconocido() {
        UUID usuario = UUID.randomUUID();

        assertThatThrownBy(() -> insertarCuenta(usuario, "CRIPTO", "Monedero digital", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("cuentas_tipo_check");
    }

    /**
     * Un usuario no puede tener dos cuentas con el mismo nombre, aunque solo cambien las
     * mayúsculas: "Nómina" y "NÓMINA" serían la misma cuenta a ojos de la persona.
     */
    @Test
    void rechaza_dos_cuentas_del_mismo_usuario_con_el_mismo_nombre_sin_importar_mayusculas() {
        UUID usuario = UUID.randomUUID();
        insertarCuenta(usuario, "AHORROS", "Nómina", false);

        assertThatThrownBy(() -> insertarCuenta(usuario, "CORRIENTE", "NÓMINA", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ix_cuentas_nombre");
    }

    /**
     * La regla del nombre es por usuario: dos personas distintas pueden llamar igual a sus
     * cuentas sin estorbarse.
     */
    @Test
    void permite_el_mismo_nombre_a_usuarios_distintos() {
        UUID primerUsuario = UUID.randomUUID();
        UUID segundoUsuario = UUID.randomUUID();

        insertarCuenta(primerUsuario, "EFECTIVO", "Cartera", false);
        insertarCuenta(segundoUsuario, "EFECTIVO", "Cartera", false);

        Integer cuentas = jdbc.queryForObject(
                "SELECT count(*) FROM cuentas WHERE usuario_ref IN (?, ?) AND nombre = 'Cartera'",
                Integer.class, primerUsuario, segundoUsuario);
        assertThat(cuentas).isEqualTo(2);
    }

    /**
     * Cada usuario tiene como mucho una cuenta predeterminada, la que se le sugiere al registrar un
     * movimiento. Si hubiera dos, el servicio no sabría cuál proponer.
     */
    @Test
    void rechaza_dos_cuentas_predeterminadas_del_mismo_usuario() {
        UUID usuario = UUID.randomUUID();
        insertarCuenta(usuario, "CORRIENTE", "Banco principal", true);

        assertThatThrownBy(() -> insertarCuenta(usuario, "AHORROS", "Banco secundario", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ix_cuentas_predeterminada");
    }

    /**
     * Principio de mínimo privilegio: el rol de la aplicación puede leer, crear, modificar y borrar
     * cuentas, y nada más. No puede vaciar la tabla de golpe ({@code TRUNCATE}), ni colgarle
     * disparadores, ni darle mantenimiento; eso queda para quien aplica las migraciones.
     *
     * <p>Funciona aunque el rol de pruebas no pueda iniciar sesión: {@code has_table_privilege}
     * no se conecta como ese rol, solo consulta los permisos que tiene concedidos, ya sea
     * directamente o a través de {@code PUBLIC}.</p>
     */
    @Test
    void la_aplicacion_tiene_exactamente_los_permisos_de_datos_sobre_las_cuentas() {
        assertThat(permisosConcedidos("cuentas"))
                .containsExactlyInAnyOrder("SELECT", "INSERT", "UPDATE", "DELETE");
    }

    /**
     * El historial de migraciones es la tabla donde Flyway anota qué archivos ya aplicó. El rol de
     * la aplicación no debe poder leerla ni cambiarla: si pudiera, un fallo o un ataque en el
     * servicio podría hacer que una migración se repita o se dé por aplicada sin estarlo.
     */
    @Test
    void la_aplicacion_no_tiene_permisos_sobre_el_historial_de_migraciones() {
        assertThat(permisosConcedidos("flyway_schema_history")).isEmpty();
    }

    /**
     * Pregunta a PostgreSQL, permiso por permiso, cuáles tiene el rol de la aplicación sobre una
     * tabla.
     *
     * @param tabla nombre de la tabla en el esquema público
     * @return los permisos concedidos, con el nombre que usa PostgreSQL (por ejemplo {@code SELECT})
     */
    private List<String> permisosConcedidos(String tabla) {
        List<String> concedidos = new ArrayList<>();
        for (String permiso : PERMISOS_DE_TABLA) {
            Boolean loTiene = jdbc.queryForObject(
                    "SELECT has_table_privilege(?, ?, ?)",
                    Boolean.class, ROL_APLICACION, "public." + tabla, permiso);
            if (Boolean.TRUE.equals(loTiene)) {
                concedidos.add(permiso);
            }
        }
        return concedidos;
    }

    /**
     * Inserta una cuenta con lo mínimo que exige la tabla; el resto de columnas toma su valor por
     * defecto.
     *
     * @param usuario        dueño de la cuenta
     * @param tipo           tipo de cuenta tal como se guarda (por ejemplo {@code AHORROS})
     * @param nombre         nombre visible de la cuenta
     * @param predeterminada si es la cuenta sugerida por defecto
     */
    private void insertarCuenta(UUID usuario, String tipo, String nombre, boolean predeterminada) {
        jdbc.update(
                """
                INSERT INTO cuentas (id, usuario_ref, tipo, nombre, fecha_inicio, predeterminada)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), usuario, tipo, nombre, LocalDate.of(2026, 1, 1), predeterminada);
    }
}
