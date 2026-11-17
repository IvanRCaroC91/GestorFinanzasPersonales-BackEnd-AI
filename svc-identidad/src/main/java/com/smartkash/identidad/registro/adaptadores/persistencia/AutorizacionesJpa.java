package com.smartkash.identidad.registro.adaptadores.persistencia;

import org.springframework.data.repository.Repository;

import java.util.UUID;

/**
 * Operaciones sobre la tabla de autorizaciones.
 *
 * <p>Qué es: una interfaz de Spring Data; Spring crea su código al arrancar. Solo declara
 * guardar, porque la tabla es de solo inserción. La usa
 * {@link RepositorioAutorizacionesPostgres}.</p>
 */
interface AutorizacionesJpa extends Repository<AutorizacionEntidad, UUID> {

    /**
     * Inserta la fila y la envía a la base de datos en el momento, sin esperar al final de
     * la transacción. Así, si la base la rechaza (por ejemplo, porque la cuenta no existe),
     * el error aparece aquí y no más tarde.
     *
     * @param fila la autorización a insertar
     * @return la misma fila ya guardada
     */
    AutorizacionEntidad saveAndFlush(AutorizacionEntidad fila);
}