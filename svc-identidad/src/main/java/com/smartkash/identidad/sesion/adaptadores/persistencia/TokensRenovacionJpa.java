package com.smartkash.identidad.sesion.adaptadores.persistencia;

import org.springframework.data.repository.Repository;

import java.util.UUID;

/**
 * Operaciones sobre la tabla de tokens de renovación.
 *
 * <p>Qué es: una interfaz de Spring Data; Spring crea su código al arrancar. Por ahora solo
 * declara guardar. La usa {@link RepositorioTokensRenovacionPostgres}.</p>
 */
interface TokensRenovacionJpa extends Repository<TokenRenovacionEntidad, UUID> {

    /**
     * Inserta la fila y la envía a la base de datos en el momento, sin esperar al final de
     * la transacción. Así, si la base la rechaza (por ejemplo, porque la cuenta no existe),
     * el error aparece aquí y no más tarde.
     *
     * @param fila el token a insertar
     * @return la misma fila ya guardada
     */
    TokenRenovacionEntidad saveAndFlush(TokenRenovacionEntidad fila);
}