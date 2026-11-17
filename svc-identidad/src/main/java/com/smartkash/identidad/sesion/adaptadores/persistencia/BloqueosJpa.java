package com.smartkash.identidad.sesion.adaptadores.persistencia;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Consultas sobre la tabla de contadores de acceso.
 *
 * <p>Qué es: una interfaz de Spring Data; Spring crea su código al arrancar. Solo la usa
 * {@link RepositorioBloqueosPostgres}, y todas las consultas que modifican la tabla deben
 * llamarse dentro de una transacción abierta por él.</p>
 */
interface BloqueosJpa extends Repository<BloqueoEntidad, String> {

    /**
     * Lee un contador sin bloquear nada. Sirve para consultar si hay que esperar.
     *
     * @param claveHash huella que identifica al contador
     * @return la fila, o una caja vacía si no existe
     */
    Optional<BloqueoEntidad> findById(String claveHash);

    /**
     * Lee un contador y bloquea su fila hasta que termine la transacción.
     *
     * <p>Qué es un bloqueo pesimista: se reserva la fila antes de modificarla («pesimista»
     * porque se da por hecho que alguien más puede querer tocarla a la vez). Si otra
     * transacción pide la misma fila, espera a que esta termine y entonces lee el valor ya
     * actualizado. En PostgreSQL se traduce en {@code SELECT ... FOR UPDATE} (o su variante
     * {@code FOR NO KEY UPDATE}). Así, dos fallos simultáneos se anotan uno detrás de otro y
     * ninguno se pierde.</p>
     *
     * @param claveHash huella que identifica al contador
     * @return la fila bloqueada, o una caja vacía si no existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BloqueoEntidad b WHERE b.claveHash = :claveHash")
    Optional<BloqueoEntidad> buscarBloqueandoLaFila(@Param("claveHash") String claveHash);

    /**
     * Crea un contador solo si todavía no existe.
     *
     * <p>Por qué con SQL propio: si dos fallos del mismo contador llegan a la vez y ninguno
     * encuentra la fila, los dos intentarán crearla. {@code ON CONFLICT DO NOTHING} hace que
     * el segundo no inserte ni lance un error: PostgreSQL lo hace esperar a que el primero
     * termine y después responde que no insertó nada. Quien recibe 0 sabe que la fila ya
     * existe y la vuelve a buscar.</p>
     *
     * @param claveHash      huella que identifica al contador
     * @param tipo           {@code CUENTA} o {@code IP}
     * @param fallos         fallos iniciales
     * @param ventanaInicio  momento del primer fallo
     * @param bloqueadoHasta fin del bloqueo, o {@code null} si no hay
     * @return 1 si se creó la fila; 0 si ya existía
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO bloqueos_acceso (clave_hash, tipo, fallos, ventana_inicio, bloqueado_hasta)
            VALUES (:claveHash, :tipo, :fallos, :ventanaInicio, :bloqueadoHasta)
            ON CONFLICT (clave_hash) DO NOTHING
            """)
    int insertarSiNoExiste(
            @Param("claveHash") String claveHash,
            @Param("tipo") String tipo,
            @Param("fallos") short fallos,
            @Param("ventanaInicio") Instant ventanaInicio,
            @Param("bloqueadoHasta") Instant bloqueadoHasta);

    /**
     * Borra un contador. Si no existe, no hace nada.
     *
     * @param claveHash huella que identifica al contador
     * @return cuántas filas se borraron: 1 o 0
     */
    @Modifying
    @Query("DELETE FROM BloqueoEntidad b WHERE b.claveHash = :claveHash")
    int borrar(@Param("claveHash") String claveHash);
}