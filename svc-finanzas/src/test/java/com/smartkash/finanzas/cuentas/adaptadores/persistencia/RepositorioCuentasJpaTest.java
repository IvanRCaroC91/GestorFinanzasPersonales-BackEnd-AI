package com.smartkash.finanzas.cuentas.adaptadores.persistencia;

import com.smartkash.finanzas.PruebaConContenedores;
import com.smartkash.finanzas.cuentas.dominio.Cuenta;
import com.smartkash.finanzas.cuentas.dominio.TipoDeCuenta;
import com.smartkash.finanzas.cuentas.puertos.RepositorioCuentas;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Comprueba que el adaptador JPA lee las cuentas de un PostgreSQL real y las convierte bien en
 * cuentas del negocio.
 *
 * <p>Qué hace: arranca el servicio contra el PostgreSQL desechable que comparten las pruebas,
 * siembra filas directamente con SQL ({@link JdbcTemplate}) y las pide a través del puerto
 * {@link RepositorioCuentas}, igual que lo hará el caso de uso. Sembrar con SQL y no con el
 * propio código asegura que se prueba la lectura sola: si el adaptador escribiera y leyera mal
 * de la misma manera, los dos errores se taparían entre sí.</p>
 *
 * <p>Por qué está en este paquete: la entidad y la interfaz de Spring Data son privadas al
 * paquete. La prueba no las usa directamente; pide el bean por el puerto, que es lo que verá el
 * resto del servicio.</p>
 *
 * <p>Independencia: cada prueba usa usuarios al azar, así que lo que deja una en la base
 * compartida no afecta a otra.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
class RepositorioCuentasJpaTest extends PruebaConContenedores {

    private static final LocalDate FECHA = LocalDate.of(2026, 1, 1);

    @Autowired
    private RepositorioCuentas repositorio;

    @Autowired
    private JdbcTemplate jdbc;

    /** Una persona sin cuentas recibe una lista vacía, no un error ni un nulo. */
    @Test
    void devuelve_una_lista_vacia_si_el_usuario_no_tiene_cuentas() {
        assertThat(repositorio.buscarCuentasUsuario(UUID.randomUUID())).isEmpty();
    }

    /**
     * Cada columna llega a su campo: identificador, tipo convertido al {@code enum}, nombre,
     * saldo con sus céntimos y su signo, fecha de inicio y los dos indicadores. Se usan valores
     * distintos de los de por defecto (tipo distinto de efectivo, saldo negativo, cuenta
     * predeterminada) para que un campo mal copiado no pase desapercibido.
     */
    @Test
    void devuelve_la_cuenta_con_todos_sus_datos() {
        UUID usuario = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO cuentas (id, usuario_ref, tipo, nombre, saldo_inicial, fecha_inicio,
                                     predeterminada, archivada)
                VALUES (?, ?, 'BILLETERA', 'Monedero digital', -1250.50, ?, true, false)
                """,
                id, usuario, LocalDate.of(2025, 12, 31));

        List<Cuenta> cuentas = repositorio.buscarCuentasUsuario(usuario);

        assertThat(cuentas).containsExactly(new Cuenta(
                id, TipoDeCuenta.BILLETERA, "Monedero digital", new BigDecimal("-1250.50"),
                LocalDate.of(2025, 12, 31), true, false));
    }

    /**
     * Las cuentas salen ordenadas por nombre, de la A a la Z, aunque se hayan guardado en otro
     * orden.
     *
     * <p>Por qué se apagan los índices: el índice único de nombre por usuario guarda las filas
     * ya ordenadas. Si PostgreSQL lo usa para buscar, devuelve las cuentas en orden aunque la
     * consulta no lo pida, y la prueba pasaría por casualidad. Con más datos, PostgreSQL puede
     * elegir otro camino y ese orden accidental desaparece. Por eso aquí se le obliga a leer la
     * tabla entera: así solo un {@code ORDER BY} en la consulta deja las cuentas en orden.</p>
     *
     * <p>Qué es {@code @Transactional} en una prueba: todo el método corre en una sola
     * transacción, que Spring deshace al terminar. Eso permite que {@code SET LOCAL} (que solo
     * vale dentro de la transacción actual) afecte también a la consulta del repositorio, y no
     * deja las filas sembradas en la base.</p>
     *
     * <p>Los tres nombres empiezan por mayúscula a propósito. Cómo se ordenan las mayúsculas
     * frente a las minúsculas no lo decide el código, sino el cotejamiento de la base de datos:
     * con {@code en_US.utf8} (el de estas pruebas) "alfa" va antes que "Banco", y con {@code C}
     * iría después. Esta prueba protege el orden, no esa decisión.</p>
     */
    @Test
    @Transactional
    void ordena_las_cuentas_por_nombre_ascendente() {
        jdbc.execute("SET LOCAL enable_indexscan = off");
        jdbc.execute("SET LOCAL enable_bitmapscan = off");
        jdbc.execute("SET LOCAL enable_indexonlyscan = off");
        UUID usuario = UUID.randomUUID();
        insertarCuenta(usuario, "Cartera", false);
        insertarCuenta(usuario, "Ahorros", false);
        insertarCuenta(usuario, "Banco", false);

        List<Cuenta> cuentas = repositorio.buscarCuentasUsuario(usuario);

        assertThat(cuentas).extracting(Cuenta::nombre).containsExactly("Ahorros", "Banco", "Cartera");
    }

    /**
     * Aislamiento entre personas: aunque otra persona tenga cuentas, incluso con el mismo nombre,
     * la búsqueda solo devuelve las del usuario pedido.
     */
    @Test
    void no_devuelve_cuentas_de_otro_usuario() {
        UUID usuario = UUID.randomUUID();
        UUID otroUsuario = UUID.randomUUID();
        UUID propia = insertarCuenta(usuario, "Ahorros", false);
        insertarCuenta(otroUsuario, "Ahorros", false);
        insertarCuenta(otroUsuario, "Cartera", false);

        List<Cuenta> cuentas = repositorio.buscarCuentasUsuario(usuario);

        assertThat(cuentas).extracting(Cuenta::id).containsExactly(propia);
    }

    /**
     * Archivar una cuenta la oculta para registros nuevos, pero no la borra: la persona tiene que
     * seguir viéndola, con su indicador de archivada.
     */
    @Test
    void devuelve_tambien_las_cuentas_archivadas() {
        UUID usuario = UUID.randomUUID();
        insertarCuenta(usuario, "Activa", false);
        insertarCuenta(usuario, "Vieja", true);

        List<Cuenta> cuentas = repositorio.buscarCuentasUsuario(usuario);

        assertThat(cuentas).extracting(Cuenta::nombre, Cuenta::archivada)
                .containsExactly(tuple("Activa", false), tuple("Vieja", true));
    }

    /**
     * Siembra una cuenta de ahorros con saldo cero.
     *
     * @param usuario   dueño de la cuenta
     * @param nombre    nombre visible
     * @param archivada si la cuenta está archivada
     * @return el identificador de la fila creada
     */
    private UUID insertarCuenta(UUID usuario, String nombre, boolean archivada) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO cuentas (id, usuario_ref, tipo, nombre, fecha_inicio, archivada)
                VALUES (?, ?, 'AHORROS', ?, ?, ?)
                """,
                id, usuario, nombre, FECHA, archivada);
        return id;
    }
}
