package com.smartkash.identidad.sesion.adaptadores.persistencia;

import com.smartkash.identidad.sesion.puertos.ConsultaDePoliticas;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Responde en PostgreSQL si una cuenta tiene una política de tratamiento pendiente.
 *
 * <p>Qué es: el adaptador del puerto {@link ConsultaDePoliticas}. Lee dos tablas que escribe
 * el registro (políticas publicadas y autorizaciones), sin modificarlas. Las lee con SQL
 * directo porque es una sola pregunta de sí o no, y no hace falta convertir filas en
 * objetos.</p>
 *
 * <p>Qué es {@code JdbcClient}: la herramienta de Spring para lanzar SQL escrito a mano con
 * parámetros con nombre ({@code :usuarioId}). Los valores viajan separados del texto de la
 * consulta, así que no hay riesgo de inyección SQL.</p>
 */
@Repository
class ConsultaDePoliticasPostgres implements ConsultaDePoliticas {

    /*
     * Existe alguna política ya publicada que cambia la finalidad y para la que la cuenta no
     * tiene una aceptación de esa misma versión o de una publicada después.
     */
    private static final String HAY_PENDIENTE = """
            SELECT EXISTS (
                SELECT 1
                FROM politicas_tratamiento nueva
                WHERE nueva.cambia_finalidad
                  AND nueva.publicada_en <= :ahora
                  AND NOT EXISTS (
                      SELECT 1
                      FROM autorizaciones a
                      JOIN politicas_tratamiento aceptada ON aceptada.version = a.politica_version
                      WHERE a.usuario_id = :usuarioId
                        AND a.tipo = 'POLITICA_TRATAMIENTO'
                        AND a.otorgada
                        AND aceptada.publicada_en >= nueva.publicada_en))
            """;

    private final JdbcClient jdbc;

    /**
     * @param jdbc cliente de SQL; lo entrega Spring
     */
    ConsultaDePoliticasPostgres(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Dice si la cuenta tiene una política pendiente de aceptar.
     *
     * @param usuarioId la cuenta
     * @param ahora     momento de la consulta
     * @return {@code true} si debe aceptar una política nueva
     */
    @Override
    public boolean hayPoliticaPendiente(UUID usuarioId, Instant ahora) {
        return jdbc.sql(HAY_PENDIENTE)
                .param("usuarioId", usuarioId)
                .param("ahora", Timestamp.from(ahora))
                .query(Boolean.class)
                .single();
    }
}