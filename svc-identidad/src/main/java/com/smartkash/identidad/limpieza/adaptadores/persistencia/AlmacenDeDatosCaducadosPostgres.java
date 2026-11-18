package com.smartkash.identidad.limpieza.adaptadores.persistencia;

import com.smartkash.identidad.limpieza.puertos.AlmacenDeDatosCaducados;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Borra en PostgreSQL los datos caducados, por lotes pequeños.
 *
 * <p>Por qué por lotes: un único {@code DELETE} sobre miles de filas bloquea todas esas filas
 * hasta terminar, y un inicio de sesión que necesitara una de ellas tendría que esperar. Aquí
 * cada sentencia borra como mucho {@code tamanoDelLote} filas y se confirma sola (sin
 * transacción alrededor), así que cada bloqueo dura muy poco. Se repite hasta que una vuelta
 * borra menos que un lote completo, lo que significa que ya no queda nada.</p>
 *
 * <p>Cómo es cada sentencia: la subconsulta elige hasta un lote de claves que cumplen la
 * condición, y el {@code DELETE} las borra. La condición se repite fuera de la subconsulta a
 * propósito. Si mientras tanto un inicio de sesión actualiza un contador elegido (por ejemplo,
 * le anota un fallo nuevo), PostgreSQL vuelve a evaluar la condición de fuera sobre la fila ya
 * actualizada, y como ya no la cumple, no la borra. Sin esa repetición, se borraría un
 * contador con actividad reciente y la persona recuperaría intentos.</p>
 *
 * <p>Qué es {@code JdbcClient}: la herramienta de Spring para lanzar SQL escrito a mano con
 * parámetros, que nunca se pegan al texto de la consulta.</p>
 *
 * <p>Límite conocido: ninguna de las tres tablas tiene un índice por fecha, así que cada
 * vuelta recorre la tabla entera. Con una ejecución al día y los volúmenes previstos es
 * aceptable; si la tabla de contadores creciera mucho, convendría un índice nuevo.</p>
 */
@Repository
class AlmacenDeDatosCaducadosPostgres implements AlmacenDeDatosCaducados {

    private static final String CONTADORES = """
            DELETE FROM bloqueos_acceso
            WHERE clave_hash IN (
                    SELECT clave_hash FROM bloqueos_acceso
                    WHERE GREATEST(ventana_inicio, bloqueado_hasta) < :limite
                    LIMIT :lote)
              AND GREATEST(ventana_inicio, bloqueado_hasta) < :limite
            """;

    private static final String TOKENS_DE_RENOVACION = """
            DELETE FROM tokens_renovacion
            WHERE id IN (
                    SELECT id FROM tokens_renovacion
                    WHERE expira_en < :limite
                    LIMIT :lote)
              AND expira_en < :limite
            """;

    private static final String ENLACES_DE_UN_SOLO_USO = """
            DELETE FROM tokens_un_solo_uso
            WHERE id IN (
                    SELECT id FROM tokens_un_solo_uso
                    WHERE expira_en < :limite
                    LIMIT :lote)
              AND expira_en < :limite
            """;

    /** Filas que borra cada sentencia en el servicio: pocas para que cada bloqueo dure poco. */
    static final int LOTE = 1_000;

    private final JdbcClient jdbc;
    private final int tamanoDelLote;

    /**
     * Crea el adaptador con el lote del servicio. Es el constructor que usa Spring.
     *
     * @param jdbc el cliente SQL de Spring
     */
    @Autowired
    AlmacenDeDatosCaducadosPostgres(JdbcClient jdbc) {
        this(jdbc, LOTE);
    }

    /**
     * Crea el adaptador con un lote a medida; las pruebas lo usan con lotes pequeños.
     *
     * @param jdbc          el cliente SQL de Spring
     * @param tamanoDelLote cuántas filas borra cada sentencia como mucho; al menos 1
     * @throws IllegalArgumentException si el lote es menor que 1
     */
    AlmacenDeDatosCaducadosPostgres(JdbcClient jdbc, int tamanoDelLote) {
        if (tamanoDelLote < 1) {
            throw new IllegalArgumentException("El lote debe ser de al menos una fila.");
        }
        this.jdbc = jdbc;
        this.tamanoDelLote = tamanoDelLote;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code GREATEST} devuelve la mayor de las dos fechas e ignora la que esté vacía: un
     * contador sin bloqueo se mide solo por el inicio de su ventana.</p>
     */
    @Override
    public long borrarContadoresSinActividadDesde(Instant limite) {
        return borrarPorLotes(CONTADORES, limite);
    }

    /** {@inheritDoc} */
    @Override
    public long borrarTokensDeRenovacionCaducadosAntesDe(Instant limite) {
        return borrarPorLotes(TOKENS_DE_RENOVACION, limite);
    }

    /** {@inheritDoc} */
    @Override
    public long borrarEnlacesDeUnSoloUsoCaducadosAntesDe(Instant limite) {
        return borrarPorLotes(ENLACES_DE_UN_SOLO_USO, limite);
    }

    private long borrarPorLotes(String sentencia, Instant limite) {
        long total = 0;
        int borradas;
        do {
            borradas = jdbc.sql(sentencia)
                    .param("limite", Timestamp.from(limite))
                    .param("lote", tamanoDelLote)
                    .update();
            total += borradas;
        } while (borradas == tamanoDelLote);
        return total;
    }
}
